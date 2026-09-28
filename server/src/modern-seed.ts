import {query,pool} from './db.js';
import {randomUUID} from 'node:crypto';
import {manifestSchema} from './manifest.js';
const rows=await query("SELECT t.id,t.manifest FROM themes t JOIN users u ON u.id=t.author_id WHERE u.username='homelystudio'");
for(const row of rows){
 if(row.manifest.widgets?.length)continue;
 const widgets=[{id:randomUUID(),name:'Relógio',kind:'clock',text:'',foreground:'#FFFFFF',background:row.manifest.palette.surface,accent:row.manifest.palette.accent,radius:24,height:160,fontSize:48},{id:randomUUID(),name:'No seu ritmo',kind:'text',text:'Um novo começo.',foreground:'#FFFFFF',background:row.manifest.palette.surface,accent:row.manifest.palette.accent,radius:24,height:100,fontSize:22}];
 const manifest=manifestSchema.parse({...row.manifest,schemaVersion:'1.2',widgets,layout:{columns:4,iconSize:54,showLabels:true}});
 await query('UPDATE themes SET manifest=$2 WHERE id=$1',[row.id,manifest]);
}
console.log('Editorial themes now include functional widget definitions.');
await pool.end();
