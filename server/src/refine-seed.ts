import {randomUUID,createHash} from 'node:crypto';
import {readFile,writeFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {query,pool} from './db.js';
import {manifestSchema} from './manifest.js';
const dir=resolve(process.env.STORAGE_DIR||'storage');
const entries=JSON.parse(await readFile(new URL('../assets/catalog.json',import.meta.url),'utf8'));
const [author]=await query("SELECT id FROM users WHERE username='homelystudio'");
if(!author)throw new Error('Run initial seed first.');
async function media(file:string,kind:string,name:string,extra:Record<string,string>={}){
 const bytes=await readFile(new URL('../assets/'+file,import.meta.url));
 const hash=createHash('sha256').update(bytes).digest('hex');
 let [row]=await query('SELECT * FROM media WHERE owner_id=$1 AND sha256=$2 LIMIT 1',[author.id,hash]);
 if(!row){
  const id=randomUUID(),filename=id+'.'+file.split('.').pop(),mime=file.endsWith('.webp')?'image/webp':'image/png';
  await writeFile(join(dir,filename),bytes);
  [row]=await query('INSERT INTO media(id,owner_id,filename,mime,size,sha256) VALUES($1,$2,$3,$4,$5,$6) RETURNING *',[id,author.id,filename,mime,bytes.length,hash]);
 }
 return {id:row.id,kind,name,url:'/media/'+row.filename,mime:row.mime,size:Number(row.size),sha256:row.sha256,...extra};
}
for(const entry of entries){
 const [theme]=await query('SELECT id,manifest FROM themes WHERE author_id=$1 AND title=$2',[author.id,entry.title]);
 if(!theme||theme.manifest.assets.some((a:any)=>a.kind==='screenshot'))continue;
 const assets=theme.manifest.assets.filter((a:any)=>a.kind!=='icon');
 const style=['Minimalista','Colorido','3D'][entry.refinedStyle];
 for(const [key,name,pkg,category]of [['phone','Telefone','com.google.android.dialer','Comunicação'],['camera','Câmera','com.google.android.GoogleCamera','Foto e vídeo'],['chat','Mensagens','com.google.android.apps.messaging','Comunicação'],['browser','Navegador','com.android.chrome','Navegação']]){
  assets.push(await media('refined-'+['minimal','color','3d'][entry.refinedStyle]+'-'+key+'.png','icon',name,{packageName:pkg,category,style}));
 }
 for(const [index,file]of entry.screenshots.entries())assets.push(await media(file,'screenshot',index===0?'Tela de bloqueio':'Tela inicial'));
 const manifest=manifestSchema.parse({...theme.manifest,schemaVersion:'1.1',assets});
 await query('UPDATE themes SET manifest=$2 WHERE id=$1',[theme.id,manifest]);
}
console.log('Editorial galleries and categorized icons updated. Community posts were untouched.');
await pool.end();
