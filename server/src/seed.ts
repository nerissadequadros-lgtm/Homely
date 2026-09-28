import { randomUUID, createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { query, pool } from './db.js';
import { hashPassword } from './auth.js';
import { manifestSchema } from './manifest.js';
const dir=resolve(process.env.STORAGE_DIR||'storage');await mkdir(dir,{recursive:true});
const catalog=JSON.parse(await readFile(new URL('../assets/catalog.json',import.meta.url),'utf8'));
const [existing]=await query("SELECT id FROM users WHERE username='homelystudio'");
const author=existing?.id||randomUUID();
if(!existing)await query('INSERT INTO users(id,username,email,password_hash,display_name,bio) VALUES($1,$2,$3,$4,$5,$6)',[author,'homelystudio','studio@homely.invalid',await hashPassword(randomUUID()+randomUUID()),'Homely Studio','Um celular. Infinitas possibilidades. Coleção original Homely, livre para remixar.']);
async function media(filename:string,kind:string,name:string,packageName?:string){
 const bytes=await readFile(new URL('../assets/'+filename,import.meta.url));const id=randomUUID();const dest=id+'.'+filename.split('.').pop();await writeFile(join(dir,dest),bytes);
 const mime=filename.endsWith('.mp4')?'video/mp4':'image/png',sha256=createHash('sha256').update(bytes).digest('hex');
 await query('INSERT INTO media(id,owner_id,filename,mime,size,sha256) VALUES($1,$2,$3,$4,$5,$6)',[id,author,dest,mime,bytes.length,sha256]);
 return {id,kind,name,url:'/media/'+dest,mime,size:bytes.length,sha256,...(packageName?{packageName}:{})};
}
for(const entry of catalog){
 if((await query('SELECT id FROM themes WHERE author_id=$1 AND title=$2',[author,entry.title])).length)continue;
 const assets:any[]=[await media(entry.file,'wallpaper','Wallpaper · '+entry.title)];
 if(entry.icons){for(const [file,name,pkg] of [['icon-phone.png','Telefone','com.google.android.dialer'],['icon-camera.png','Câmera','com.google.android.GoogleCamera'],['icon-chat.png','Mensagens','com.google.android.apps.messaging'],['icon-browser.png','Navegador','com.android.chrome']])assets.push(await media(file,'icon',name,pkg));}
 if(entry.video)assets.push(await media(entry.video,'live_wallpaper','Motion · '+entry.title));
 const manifest=manifestSchema.parse({widgets:[],layout:{columns:4,iconSize:54,showLabels:true},schemaVersion:'1.0',title:entry.title,description:entry.description,category:entry.category,tags:entry.tags,palette:{accent:entry.accent,background:'#090C12',surface:'#171C27'},iconStyle:entry.iconStyle||'rounded',clockStyle:'thin',assets,compatibility:{minAndroid:26,launcherRequiredForIconPack:true,systemBars:'preview-only'},license:'CC0-1.0'});
 await query('INSERT INTO themes(id,author_id,title,description,category,tags,cover_url,manifest) VALUES($1,$2,$3,$4,$5,$6,$7,$8)',[randomUUID(),author,entry.title,entry.description,entry.category,entry.tags,assets[0].url,manifest]);
}
console.log('Original Homely collection ready.');
await pool.end();
