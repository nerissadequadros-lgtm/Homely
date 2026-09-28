import type { FastifyInstance } from 'fastify';
import { randomUUID } from 'node:crypto';
import { z } from 'zod';
import { identity } from './auth.js';
import { pool,query } from './db.js';
import { categories } from './manifest.js';
import { dailyChoice,emptyProfile,features,learn,rank,type PreferenceProfile } from './recommendation-model.js';

const uuid=z.string().uuid();
const timezone=z.string().max(80).refine(v=>{try{new Intl.DateTimeFormat('en',{timeZone:v});return true;}catch{return false;}});
const event=z.object({id:uuid,kind:z.enum(['view','dwell','category','collection','recommendation_open','apply']),themeId:uuid.optional(),context:z.string().max(80).optional(),duration:z.number().int().min(0).max(300).default(0)}).strict();
export async function ensurePreferences(user:string) {await query('INSERT INTO discovery_preferences(user_id) VALUES($1) ON CONFLICT DO NOTHING',[user]);}
export async function rankedThemes(user:string,themeSelect:string) {
  await ensurePreferences(user);
  const [preferences]=await query('SELECT profile FROM discovery_preferences WHERE user_id=$1',[user]);
  let profile:PreferenceProfile=preferences.profile??emptyProfile();
  const liked=await query(`SELECT t.* FROM themes t WHERE EXISTS(SELECT 1 FROM likes WHERE user_id=$1 AND theme_id=t.id) OR EXISTS(SELECT 1 FROM saves WHERE user_id=$1 AND theme_id=t.id) ORDER BY t.created_at DESC LIMIT 200`,[user]);
  // Explicit saves/likes have more influence than a passing view and are reflected on every ranking.
  for(const theme of liked) profile=learn(profile,features(theme),5);
  const followed=new Set((await query('SELECT following_id FROM follows WHERE follower_id=$1',[user])).map(r=>r.following_id));
  const candidates=await query(themeSelect+' ORDER BY t.created_at DESC LIMIT 1000',[user]);
  return rank(candidates,profile,followed);
}
export async function scheduleUser(user:string,themeSelect:string) {
  await ensurePreferences(user);
  const [p]=await query(`SELECT *, (now() AT TIME ZONE timezone)::date::text AS today FROM discovery_preferences WHERE user_id=$1`,[user]);
  if(!p.enabled)return;
  if((await query('SELECT 1 FROM discovery_notifications WHERE user_id=$1 AND local_date=$2',[user,p.today])).length)return;
  const [last]=await query('SELECT template,minute FROM discovery_notifications WHERE user_id=$1 ORDER BY local_date DESC LIMIT 1',[user]);
  const choice=dailyChoice(user,p.today,last?.template,last?.minute);
  const themes=(await rankedThemes(user,themeSelect)).slice(0,8).map(t=>t.id);
  if(!themes.length)return; // Never advertise nonexistent themes.
  await query(`INSERT INTO discovery_notifications(id,user_id,local_date,available_at,minute,template,title,theme_ids)
    VALUES($1,$2,$3,($3::date + $4 * interval '1 minute') AT TIME ZONE $5,$4,$6,$7,$8) ON CONFLICT(user_id,local_date) DO NOTHING`,
    [randomUUID(),user,p.today,choice.minute,p.timezone,choice.template,choice.title,themes]);
}
export function registerDiscovery(app:FastifyInstance,themeSelect:string) {
  app.post('/v1/behavior',{config:{rateLimit:{max:30,timeWindow:'1 minute'}}},async req=>{
    const user=(await identity(req))!;const {events}=z.object({events:z.array(event).min(1).max(30)}).strict().parse(req.body);
    await ensurePreferences(user);const client=await pool.connect();
    try {
      await client.query('BEGIN');
      const p=(await client.query('SELECT profile,updated_at FROM discovery_preferences WHERE user_id=$1 FOR UPDATE',[user])).rows[0];
      let profile:PreferenceProfile=p.profile;
      const decay=Math.exp(-Math.max(0,Date.now()-new Date(p.updated_at).getTime())/(60*86400000));
      profile={...profile,weights:Object.fromEntries(Object.entries(profile.weights).map(([k,v])=>[k,v*decay]))};
      for(const e of events) {
        let f:string[]=[];let weight=1;
        if(e.kind==='category') {if(!categories.includes(e.context as any))continue;f=[`category:${e.context}`];}
        else if(e.kind==='collection') {
          if(!uuid.safeParse(e.context).success)continue;
          const themes=(await client.query('SELECT t.* FROM themes t JOIN saves s ON t.id=s.theme_id WHERE s.user_id=$1 AND s.collection_id=$2 LIMIT 20',[user,e.context])).rows;
          f=[...new Set(themes.flatMap(features))];weight=.25;
        } else {
          if(!e.themeId)continue;
          const theme=(await client.query('SELECT * FROM themes WHERE id=$1',[e.themeId])).rows[0];if(!theme)continue;
          f=features(theme);weight=e.kind==='apply'?8:e.kind==='recommendation_open'?3:e.kind==='dwell'?Math.min(3,e.duration/30):1;
        }
        const inserted=await client.query('INSERT INTO behavior_events(id,user_id,kind,theme_id,context,duration) VALUES($1,$2,$3,$4,$5,$6) ON CONFLICT DO NOTHING RETURNING id',[e.id,user,e.kind,e.themeId??null,e.context??null,e.duration]);
        if(inserted.rowCount) profile=learn(profile,f,weight,e.kind==='view'?e.themeId:undefined);
      }
      await client.query('UPDATE discovery_preferences SET profile=$2,updated_at=now() WHERE user_id=$1',[user,profile]);
      await client.query('COMMIT');return {ok:true};
    }catch(e){await client.query('ROLLBACK');throw e;}finally{client.release();}
  });
  app.get('/v1/recommendations',async req=>{
    const user=(await identity(req))!;const q=z.object({offset:z.coerce.number().int().min(0).max(1000).default(0),limit:z.coerce.number().int().min(1).max(40).default(20)}).parse(req.query);
    const rows=await rankedThemes(user,themeSelect);return {items:rows.slice(q.offset,q.offset+q.limit),nextOffset:rows.length>q.offset+q.limit?q.offset+q.limit:null};
  });
  app.put('/v1/discovery/preferences',async req=>{
    const user=(await identity(req))!;const b=z.object({timezone,enabled:z.boolean()}).strict().parse(req.body);
    await ensurePreferences(user);await query('UPDATE discovery_preferences SET timezone=$2,enabled=$3 WHERE user_id=$1',[user,b.timezone,b.enabled]);
    await scheduleUser(user,themeSelect);return b;
  });
  app.get('/v1/notifications',async req=>{
    const user=(await identity(req))!;await scheduleUser(user,themeSelect);
    const rows=await query(`SELECT id,title,theme_ids AS "themeIds",available_at AS "createdAt",read_at IS NOT NULL AS read FROM discovery_notifications WHERE user_id=$1 AND available_at<=now() ORDER BY available_at DESC LIMIT 60`,[user]);
    const [count]=await query('SELECT count(*)::int AS unread FROM discovery_notifications WHERE user_id=$1 AND available_at<=now() AND read_at IS NULL',[user]);
    return {items:rows,unread:count.unread};
  });
  app.put('/v1/notifications/:id/read',async req=>{const user=await identity(req),id=uuid.parse((req.params as any).id);await query('UPDATE discovery_notifications SET read_at=coalesce(read_at,now()) WHERE id=$1 AND user_id=$2',[id,user]);return {ok:true};});
  app.get('/v1/notifications/:id/themes',async req=>{
    const user=await identity(req),id=uuid.parse((req.params as any).id);
    const [item]=await query('UPDATE discovery_notifications SET opened_at=now(),read_at=coalesce(read_at,now()) WHERE id=$1 AND user_id=$2 AND available_at<=now() RETURNING theme_ids',[id,user]);
    if(!item)throw Object.assign(new Error('Notificação não encontrada.'),{statusCode:404});
    const themes=await query(themeSelect+' WHERE t.id=ANY($2::uuid[]) ORDER BY array_position($2::uuid[],t.id)',[user,item.theme_ids]);
    return {items:themes,nextOffset:null};
  });
}
export function startDiscoveryScheduler(app:FastifyInstance,themeSelect:string) {
  let running=false;
  const tick=async()=>{if(running)return;running=true;try{
    const users=await query(`SELECT u.id FROM users u LEFT JOIN discovery_preferences p ON p.user_id=u.id WHERE coalesce(p.enabled,true) AND NOT EXISTS(SELECT 1 FROM discovery_notifications n WHERE n.user_id=u.id AND n.local_date=(now() AT TIME ZONE coalesce(p.timezone,'America/Sao_Paulo'))::date) ORDER BY u.id LIMIT 100`);
    for(const user of users)await scheduleUser(user.id,themeSelect);
    await query("DELETE FROM behavior_events WHERE created_at < now()-interval '90 days'");
    await query("DELETE FROM discovery_notifications WHERE available_at < now()-interval '90 days'");
  }catch(err){app.log.error({err},'Discovery scheduler failed');}finally{running=false;}};
  const timer=setInterval(()=>void tick(),60000);timer.unref();void tick();
  app.addHook('onClose',async()=>{clearInterval(timer);});
}
