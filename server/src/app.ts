import { registerDiscovery,startDiscoveryScheduler } from './discovery.js';
import { registerWeather } from './weather.js';
import Fastify from 'fastify';
import multipart from '@fastify/multipart';
import rateLimit from '@fastify/rate-limit';

import { randomUUID, createHash } from 'node:crypto';
import { mkdir, writeFile, unlink, stat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { resolve, join } from 'node:path';
import { fileTypeFromBuffer } from 'file-type';
import archiver from 'archiver';
import { z, ZodError } from 'zod';
import { query, pool } from './db.js';
import { identity, newSession, hashPassword, verifyPassword, digest } from './auth.js';
import { registerCatalog } from './catalog.js';
import { manifestSchema } from './manifest.js';

const storage = resolve(process.env.STORAGE_DIR || 'storage');
const bad = (message: string, statusCode = 400) => Object.assign(new Error(message), { statusCode });
const uuid = z.string().uuid();
const username = z.string().trim().toLowerCase().regex(/^[a-z][a-z0-9_]{2,23}$/);
const profileSchema = z.object({ username, displayName:z.string().trim().min(1).max(50), bio:z.string().max(150), location:z.string().max(80), website:z.union([z.literal(''), z.string().url().max(200).refine(v=>v.startsWith('https://'))]), avatarUrl:z.string().nullable().optional(), bannerUrl:z.string().nullable().optional() }).strict();
const themeSelect = `SELECT t.id,t.title,t.description,t.category,t.tags,t.cover_url AS "coverUrl",t.manifest,t.downloads,t.created_at AS "createdAt",
 json_build_object('id',u.id,'username',u.username,'displayName',u.display_name,'avatarUrl',u.avatar_url,'bannerUrl',u.banner_url) AS author,
 (SELECT count(*)::int FROM likes l WHERE l.theme_id=t.id) AS likes,
 EXISTS(SELECT 1 FROM likes l WHERE l.theme_id=t.id AND l.user_id=$1) AS liked,
 EXISTS(SELECT 1 FROM saves s WHERE s.theme_id=t.id AND s.user_id=$1) AS saved
 FROM themes t JOIN users u ON u.id=t.author_id`;
async function getProfile(id:string, viewer:string|null) {
 const [user]=await query(`SELECT id,username,display_name AS "displayName",bio,location,website,avatar_url AS "avatarUrl",banner_url AS "bannerUrl",
 (SELECT count(*)::int FROM themes WHERE author_id=users.id) AS "themeCount",
 (SELECT count(*)::int FROM follows WHERE following_id=users.id) AS followers,
 (SELECT count(*)::int FROM follows WHERE follower_id=users.id) AS following,
 EXISTS(SELECT 1 FROM follows WHERE follower_id=$2 AND following_id=users.id) AS followed
 FROM users WHERE id=$1`,[id,viewer]);
 if(!user) throw bad('Perfil não encontrado.',404); return user;
}
async function validateOwnedMedia(userId:string, assets:any[]) {
 const rows=await query('SELECT * FROM media WHERE owner_id=$1 AND id=ANY($2::uuid[])',[userId,assets.map(a=>a.id)]);
 for(const asset of assets) {
  const m=rows.find(row=>row.id===asset.id);
  if(!m || asset.url!==`/media/${m.filename}` || asset.sha256!==m.sha256 || asset.size!==Number(m.size) || asset.mime!==m.mime)
   throw bad('Um recurso não pertence à sua conta ou foi alterado.');
 }
}
export async function createApp() {
 await mkdir(storage,{recursive:true});
 const app=Fastify({logger:process.env.NODE_ENV==='test'?false:{level:process.env.LOG_LEVEL||'info',redact:['req.headers.authorization','req.headers.cookie']},trustProxy:'127.0.0.1',bodyLimit:512*1024});
 await app.register(rateLimit,{max:180,timeWindow:'1 minute'});
 await app.register(multipart,{limits:{fileSize:50*1024*1024,files:1,fields:4}});
 app.get('/media/:filename',async(req,reply)=>{
  const name=(req.params as {filename:string}).filename;
  if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(png|jpg|webp|mp4|ttf|otf)$/.test(name))throw bad('Arquivo não encontrado.',404);
  const file=join(storage,name);
  const info=await stat(file).catch(()=>null);
  if(!info?.isFile())throw bad('Arquivo não encontrado.',404);
  const ext=name.split('.').pop()!;
  reply.type(({png:'image/png',jpg:'image/jpeg',webp:'image/webp',mp4:'video/mp4',ttf:'font/ttf',otf:'font/otf'} as Record<string,string>)[ext]);
  reply.header('X-Content-Type-Options','nosniff').header('Cache-Control','public,max-age=31536000,immutable').header('Accept-Ranges','bytes');
  const range=req.headers.range;
  if(range){
   const match=/^bytes=(\d+)-(\d*)$/.exec(range);
   if(!match)return reply.code(416).header('Content-Range','bytes */'+info.size).send();
   const start=Number(match[1]),end=match[2]?Number(match[2]):info.size-1;
   if(!Number.isSafeInteger(start)||!Number.isSafeInteger(end)||start>end||end>=info.size)return reply.code(416).header('Content-Range','bytes */'+info.size).send();
   reply.code(206).header('Content-Range','bytes '+start+'-'+end+'/'+info.size).header('Content-Length',end-start+1);
   return reply.send(createReadStream(file,{start,end}));
  }
  reply.header('Content-Length',info.size);
  return reply.send(createReadStream(file));
 });

 app.setErrorHandler((error,req,reply)=>{
  if(error instanceof ZodError) return reply.code(400).send({error:'Confira os campos informados.',details:error.issues.map(i=>({field:i.path.join('.'),message:i.message}))});
  const e=error as any;
  if(e.code==='23505') return reply.code(409).send({error:'Este nome de usuário ou e-mail já está em uso.'});
  const status=e.statusCode||500;
  if(status>=500) req.log.error({err:error},'Request failed');
  return reply.code(status).send({error:status>=500?'Não foi possível concluir agora. Tente novamente.':e.message});
 });
 registerCatalog(app);
 registerWeather(app);
 registerDiscovery(app,themeSelect);
 if(process.env.NODE_ENV!=="test")startDiscoveryScheduler(app,themeSelect);
 app.get('/health',async()=>{await query('SELECT 1');return {status:'ok',service:'homely',version:'0.8.0'};});
 app.get('/v1/usernames/availability',{config:{rateLimit:{max:90,timeWindow:'1 minute'}}},async(req)=>{ const name=username.parse((req.query as any).username);const me=await identity(req,false);const [user]=await query('SELECT id FROM users WHERE username=$1',[name]);return {username:name,available:!user||user.id===me}; });
 app.post('/v1/auth/register',{config:{rateLimit:{max:5,timeWindow:'1 hour'}}},async(req,reply)=>{
  const body=z.object({email:z.string().email().max(254).transform(x=>x.toLowerCase()),password:z.string().min(10).max(128),username,displayName:z.string().trim().min(1).max(50)}).strict().parse(req.body);
  const id=randomUUID();
  await query('INSERT INTO users(id,username,email,password_hash,display_name) VALUES($1,$2,$3,$4,$5)',[id,body.username,body.email,await hashPassword(body.password),body.displayName]);
  await query('INSERT INTO collections VALUES($1,$2,$3)',[randomUUID(),id,'Inspirações']);
  return reply.code(201).send({token:await newSession(id),user:await getProfile(id,id)});
 });
 app.post('/v1/auth/login',{config:{rateLimit:{max:10,timeWindow:'15 minutes'}}},async(req)=>{
  const body=z.object({email:z.string().email().transform(x=>x.toLowerCase()),password:z.string().min(1).max(128)}).parse(req.body);
  const [user]=await query('SELECT * FROM users WHERE email=$1',[body.email]);
  // Execute scrypt even for unknown accounts to reduce timing differences.
  const fallback='00000000000000000000000000000000:'+ '00'.repeat(64);
  const valid=await verifyPassword(body.password,user?.password_hash||fallback);
  if(!user||!valid) throw bad('E-mail ou senha incorretos.',401);
  return {token:await newSession(user.id),user:await getProfile(user.id,user.id)};
 });
 app.post('/v1/auth/logout',async(req)=>{await identity(req); await query('DELETE FROM sessions WHERE token_hash=$1',[digest(req.headers.authorization!.slice(7))]);return {ok:true};});
 app.get('/v1/me',async(req)=>{const id=(await identity(req))!;return getProfile(id,id);});
 app.put('/v1/me',async(req)=>{
  const id=(await identity(req))!;const b=profileSchema.parse(req.body);
  for(const image of [b.avatarUrl,b.bannerUrl]) if(image) {
   const [m]=await query("SELECT id FROM media WHERE owner_id=$1 AND '/media/'||filename=$2 AND mime LIKE 'image/%'",[id,image]);
   if(!m) throw bad('Envie uma foto válida.');
  }
  await query('UPDATE users SET username=$2,display_name=$3,bio=$4,location=$5,website=$6,avatar_url=$7,banner_url=$8 WHERE id=$1',[id,b.username,b.displayName,b.bio,b.location,b.website,b.avatarUrl||null,b.bannerUrl||null]);
  return getProfile(id,id);
 });
 app.delete('/v1/me',async(req)=>{const id=await identity(req);const files=await query('SELECT filename FROM media WHERE owner_id=$1',[id]);await query('DELETE FROM users WHERE id=$1',[id]);for(const f of files) await unlink(join(storage,f.filename)).catch(()=>{});return {ok:true};});
 app.get('/v1/users/:id',async(req)=>getProfile(uuid.parse((req.params as any).id),await identity(req,false)));
 app.put('/v1/users/:id/follow',async(req)=>{
  const me=await identity(req),target=uuid.parse((req.params as any).id); const {active}=z.object({active:z.boolean()}).parse(req.body);
  if(me===target) throw bad('Você está no seu próprio perfil.');
  await getProfile(target,me);
  if(active) await query('INSERT INTO follows VALUES($1,$2) ON CONFLICT DO NOTHING',[me,target]);
  else await query('DELETE FROM follows WHERE follower_id=$1 AND following_id=$2',[me,target]);
  return getProfile(target,me);
 });
 app.get('/v1/themes',async(req)=>{
  const me=await identity(req,false);
  const q=z.object({category:z.string().max(24).optional(),search:z.string().max(100).optional(),author:uuid.optional(),saved:z.enum(['true']).optional(),liked:z.enum(['true']).optional(),collection:uuid.optional(),kind:z.enum(['icon','live_wallpaper']).optional(),offset:z.coerce.number().int().min(0).max(100000).default(0),limit:z.coerce.number().int().min(1).max(40).default(20)}).parse(req.query);
  const args:unknown[]=[me]; const conditions:string[]=[];
  const add=(s:string,v:unknown)=>{args.push(v);conditions.push(s.replace('?',`$${args.length}`));};
  if(q.category) add('t.category=?',q.category);
  if(q.search) add("(t.title||' '||t.description||' '||array_to_string(t.tags,' ')) ILIKE ?",'%'+q.search.replace(/[%_\\]/g,'\\$&')+'%');
  if(q.author) add('t.author_id=?',q.author);
  if(q.saved||q.collection) conditions.push('EXISTS(SELECT 1 FROM saves s WHERE s.theme_id=t.id AND s.user_id=$1)');
  if(q.liked) conditions.push('EXISTS(SELECT 1 FROM likes l WHERE l.theme_id=t.id AND l.user_id=$1)');
  if(q.collection) add('EXISTS(SELECT 1 FROM saves s WHERE s.theme_id=t.id AND s.user_id=$1 AND s.collection_id=?)',q.collection);
  if(q.kind) add("t.manifest->'assets' @> ?::jsonb",JSON.stringify([{kind:q.kind}]));
  args.push(q.limit+1,q.offset);
  const rows=await query(themeSelect+(conditions.length?' WHERE '+conditions.join(' AND '):'')+` ORDER BY t.created_at DESC,t.id LIMIT $${args.length-1} OFFSET $${args.length}`,args);
  return {items:rows.slice(0,q.limit),nextOffset:rows.length>q.limit?q.offset+q.limit:null};
 });
 app.get('/v1/themes/:id',async(req)=>{
  const me=await identity(req,false),id=uuid.parse((req.params as any).id);
  const [theme]=await query(themeSelect+' WHERE t.id=$2',[me,id]);if(!theme)throw bad('Tema não encontrado.',404);return theme;
 });
 app.get('/v1/themes/:id/related',async(req)=>{
  const me=await identity(req,false),id=uuid.parse((req.params as any).id);
  const rows=await query(themeSelect+' WHERE t.id<>$2 ORDER BY (t.category=(SELECT category FROM themes WHERE id=$2)) DESC, t.created_at DESC LIMIT 16',[me,id]);
  return {items:rows,nextOffset:null};
 });
 app.post('/v1/themes',async(req,reply)=>{
  const me=(await identity(req))!;const manifest=manifestSchema.parse(req.body);
  await validateOwnedMedia(me,manifest.assets);
  const id=randomUUID(),cover=manifest.assets.find(a=>a.kind==='wallpaper')!.url;
  await query('INSERT INTO themes(id,author_id,title,description,category,tags,cover_url,manifest) VALUES($1,$2,$3,$4,$5,$6,$7,$8)',[id,me,manifest.title,manifest.description,manifest.category,manifest.tags,cover,manifest]);
  return reply.code(201).send((await query(themeSelect+' WHERE t.id=$2',[me,id]))[0]);
 });
 app.delete('/v1/themes/:id',async(req)=>{const me=await identity(req),id=uuid.parse((req.params as any).id);const rows=await query('DELETE FROM themes WHERE id=$1 AND author_id=$2 RETURNING id',[id,me]);if(!rows.length)throw bad('Tema não encontrado.',404);return {ok:true};});
 app.put('/v1/themes/:id/like',async(req)=>{
  const me=await identity(req),id=uuid.parse((req.params as any).id);const {active}=z.object({active:z.boolean()}).parse(req.body);
  if(!(await query('SELECT id FROM themes WHERE id=$1',[id])).length)throw bad('Tema não encontrado.',404);
  if(active)await query('INSERT INTO likes VALUES($1,$2) ON CONFLICT DO NOTHING',[me,id]);else await query('DELETE FROM likes WHERE user_id=$1 AND theme_id=$2',[me,id]);
  return {active};
 });
 app.get('/v1/collections',async(req)=>{const me=await identity(req);return {items:await query('SELECT c.id,c.name,(SELECT count(*)::int FROM saves s WHERE s.collection_id=c.id) AS count FROM collections c WHERE c.user_id=$1 ORDER BY c.name',[me])};});
 app.post('/v1/collections',async(req,reply)=>{const me=await identity(req),{name}=z.object({name:z.string().trim().min(1).max(40)}).parse(req.body);const id=randomUUID();await query('INSERT INTO collections VALUES($1,$2,$3)',[id,me,name]);return reply.code(201).send({id,name,count:0});});
 app.put('/v1/themes/:id/save',async(req)=>{
  const me=await identity(req),id=uuid.parse((req.params as any).id);const b=z.object({active:z.boolean(),collectionId:uuid.optional()}).parse(req.body);
  if(!(await query('SELECT id FROM themes WHERE id=$1',[id])).length)throw bad('Tema não encontrado.',404);
  if(b.active) {
   const [c]=b.collectionId?await query('SELECT id FROM collections WHERE id=$1 AND user_id=$2',[b.collectionId,me]):await query('SELECT id FROM collections WHERE user_id=$1 ORDER BY name LIMIT 1',[me]);
   if(!c)throw bad('Coleção não encontrada.',404);
   await query('INSERT INTO saves VALUES($1,$2,$3) ON CONFLICT DO NOTHING',[me,id,c.id]);
  } else if(b.collectionId) await query('DELETE FROM saves WHERE user_id=$1 AND theme_id=$2 AND collection_id=$3',[me,id,b.collectionId]);
  else await query('DELETE FROM saves WHERE user_id=$1 AND theme_id=$2',[me,id]);
  return {active:b.active};
 });
 app.post('/v1/themes/:id/report',async(req,reply)=>{const me=await identity(req),id=uuid.parse((req.params as any).id);const {reason}=z.object({reason:z.string().trim().min(5).max(500)}).parse(req.body);if(!(await query('SELECT id FROM themes WHERE id=$1',[id])).length)throw bad('Tema não encontrado.',404);await query('INSERT INTO reports(id,user_id,theme_id,reason) VALUES($1,$2,$3,$4)',[randomUUID(),me,id,reason]);return reply.code(201).send({ok:true});});
 app.post('/v1/media',{config:{rateLimit:{max:600,timeWindow:'1 hour'}}},async(req,reply)=>{
  const me=(await identity(req))!;
  const [quota]=await query('SELECT COALESCE(sum(size),0)::bigint AS used FROM media WHERE owner_id=$1',[me]);
  if(Number(quota.used)>=512*1024*1024)throw bad('Sua biblioteca atingiu o limite de 512 MB.',413);
  const part=await req.file();if(!part)throw bad('Escolha um arquivo.');
  const buffer=await part.toBuffer();const type=await fileTypeFromBuffer(buffer);
  if(!type||!['image/jpeg','image/png','image/webp','video/mp4','font/ttf','font/otf'].includes(type.mime))throw bad('Use JPG, PNG, WebP, MP4, TTF ou OTF.',415);
  if(type.mime.startsWith('font/')&&buffer.length>5*1024*1024)throw bad('Fontes devem ter até 5 MB.',413);
  if(buffer.length>50*1024*1024||buffer.length+Number(quota.used)>512*1024*1024)throw bad('Arquivo excede o limite disponível.',413);
  const id=randomUUID(),filename=id+'.'+type.ext,sha256=createHash('sha256').update(buffer).digest('hex');
  await writeFile(join(storage,filename),buffer,{flag:'wx'});
  try {await query('INSERT INTO media(id,owner_id,filename,mime,size,sha256) VALUES($1,$2,$3,$4,$5,$6)',[id,me,filename,type.mime,buffer.length,sha256]);}
  catch(e) {await unlink(join(storage,filename));throw e;}
  return reply.code(201).send({id,url:'/media/'+filename,mime:type.mime,size:buffer.length,sha256});
 });
 app.get('/v1/themes/:id/download',async(req,reply)=>{
  const id=uuid.parse((req.params as any).id);
  const [theme]=await query('SELECT * FROM themes WHERE id=$1',[id]);if(!theme)throw bad('Tema não encontrado.',404);
  const manifest=manifestSchema.parse(theme.manifest);
  // Only server-owned UUID filenames may enter archives; no user-supplied paths.
  const media=await query('SELECT * FROM media WHERE id=ANY($1::uuid[])',[manifest.assets.map(a=>a.id)]);
  if(media.length!==manifest.assets.length)throw bad('Recursos indisponíveis.',409);
  const archive=archiver('zip',{zlib:{level:6}});
  const exported={...manifest,assets:manifest.assets.map(a=>({...a,url:'assets/'+media.find(m=>m.id===a.id).filename}))};
  for(const m of media)await stat(join(storage,m.filename));
  archive.append(JSON.stringify(exported,null,2),{name:'manifest.json'});
  for(const m of media)archive.append(createReadStream(join(storage,m.filename)),{name:'assets/'+m.filename});
  archive.on('error',err=>{req.log.error(err);archive.destroy(err);});
  reply.header('Content-Type','application/zip').header('Content-Disposition',`attachment; filename="homely-${id}.homely"`);
  await query('UPDATE themes SET downloads=downloads+1 WHERE id=$1',[id]);
  void archive.finalize();
  return reply.send(archive);
 });
 return app;
}
