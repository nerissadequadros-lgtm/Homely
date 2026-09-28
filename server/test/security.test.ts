import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hashPassword, verifyPassword } from '../src/auth.js';
import { manifestSchema } from '../src/manifest.js';
test('passwords use independent salts and verify without exposing plaintext',async()=>{
 const a=await hashPassword('a-long-test-password'),b=await hashPassword('a-long-test-password');
 assert.notEqual(a,b);assert.equal(await verifyPassword('a-long-test-password',a),true);assert.equal(await verifyPassword('wrong',a),false);
});
const valid={schemaVersion:'1.0',title:'Midnight',description:'',category:'Minimal',tags:[],palette:{accent:'#7965FF',background:'#090C12',surface:'#141922'},iconStyle:'rounded',clockStyle:'thin',assets:[{id:'034048a1-f822-4093-abd9-bc23acbd64bb',kind:'wallpaper',name:'Wallpaper',url:'/media/a.png',mime:'image/png',sha256:'a'.repeat(64),size:200}],compatibility:{minAndroid:26,launcherRequiredForIconPack:true,systemBars:'preview-only'},license:'CC-BY-4.0'};
test('manifest accepts v1 and rejects unversioned or mismatched content',()=>{
 assert.equal(manifestSchema.safeParse(valid).success,true);
 assert.equal(manifestSchema.safeParse({...valid,schemaVersion:'2.0'}).success,false);
 assert.equal(manifestSchema.safeParse({...valid,assets:[{...valid.assets[0],mime:'video/mp4'}]}).success,false);
 assert.equal(manifestSchema.safeParse({...valid,assets:[valid.assets[0],valid.assets[0]]}).success,false);
 assert.equal(manifestSchema.safeParse({...valid,palette:{...valid.palette,accent:'red'}}).success,false);
});

test('gallery requires version 1.1 and image media',()=>{ const shot={...valid.assets[0],id:'034048a1-f822-4093-abd9-bc23acbd64bc',kind:'screenshot'}; const gallery={...valid,schemaVersion:'1.1',assets:[...valid.assets,shot]}; assert.equal(manifestSchema.safeParse(gallery).success,true); assert.equal(manifestSchema.safeParse({...gallery,schemaVersion:'1.0'}).success,false); assert.equal(manifestSchema.safeParse({...gallery,assets:[...valid.assets,{...shot,mime:'video/mp4'}]}).success,false); });

test('widget definitions reject executable kinds and missing image assets',()=>{const widget={id:'034048a1-f822-4093-abd9-bc23acbd64bc',name:'Clock',kind:'clock',text:'',foreground:'#FFFFFF',background:'#171625',accent:'#AEA2FF',radius:24,height:150,fontSize:40};const theme={...valid,schemaVersion:'1.2',widgets:[widget]};assert.equal(manifestSchema.safeParse(theme).success,true);assert.equal(manifestSchema.safeParse({...theme,widgets:[{...widget,kind:'script'}]}).success,false);assert.equal(manifestSchema.safeParse({...theme,widgets:[{...widget,kind:'image'}]}).success,false);assert.equal(manifestSchema.safeParse({...theme,widgets:[{...widget,height:9000}]}).success,false);});

// Layer geometry and source references must survive a complete portable theme round trip.
test('studio 1.3 enforces layer bounds and editable icon source ownership references',()=>{
 const id='034048a1-f822-4093-abd9-bc23acbd64bc';const source='034048a1-f822-4093-abd9-bc23acbd64bd';
 const layer={id,type:'image',x:0,y:0,width:.5,height:.5,text:'',color:'#FFFFFF',fontSize:16,zIndex:0,visible:true,locked:false,fontFamily:'sans',imageAssetId:source};
 const widget={id,name:'Studio',kind:'custom',text:'',foreground:'#FFFFFF',background:'#171625',accent:'#AEA2FF',radius:24,height:320,fontSize:40,layers:[layer],columns:4,rows:4};
 const image={...valid.assets[0],id:source,kind:'widget_image'};
 const theme={...valid,schemaVersion:'1.3',widgets:[widget],assets:[...valid.assets,image]};
 assert.equal(manifestSchema.safeParse(theme).success,true);
 assert.equal(manifestSchema.safeParse({...theme,schemaVersion:'1.2'}).success,false);
 assert.equal(manifestSchema.safeParse({...theme,assets:valid.assets}).success,false);
 assert.equal(manifestSchema.safeParse({...theme,widgets:[{...widget,layers:[{...layer,x:.75}]}]}).success,false);
 const editor={sourceAssetId:source,background:'#112233',scale:.6,shadowRadius:4,strokeWidth:2,mask:'hexagon',rotation:90,mirrored:true};
 const icon={...valid.assets[0],id,kind:'icon',packageName:'com.android.phone',iconEditor:editor};
 assert.equal(manifestSchema.safeParse({...valid,schemaVersion:'1.3',assets:[...valid.assets,icon,{...image,kind:'icon_source',packageName:'com.android.phone'}]}).success,true);
 assert.equal(manifestSchema.safeParse({...valid,schemaVersion:'1.3',assets:[...valid.assets,icon,{...image,kind:'icon_source',packageName:'com.android.other'}]}).success,false);
});

test('studio 1.4 preserves freeform geometry, independent item properties and fonts',()=>{
 const fontId='a2700628-afbf-49f4-8e92-1682a9915a04',fileId='a2700628-afbf-49f4-8e92-1682a9915a05',widgetId='a2700628-afbf-49f4-8e92-1682a9915a06';
 const layer={id:'a2700628-afbf-49f4-8e92-1682a9915a07',type:'title',x:.123,y:.321,width:.6,height:.2,text:'Homely',color:'#FFFFFF',fontSize:24,zIndex:0,visible:true,locked:false,fontFamily:'sans',fontId,weight:700,alignment:'start',opacity:.8};
 const theme={...valid,schemaVersion:'1.4',fonts:[{id:fontId,name:'Fonte enviada',assetId:fileId}],widgets:[{id:widgetId,name:'Livre',kind:'custom',text:'',foreground:'#FFFFFF',background:'#171625',accent:'#AEA2FF',radius:24,height:320,fontSize:40,layers:[layer],columns:4,rows:4,placement:'free',gridColumns:7,gridRows:9}],assets:[...valid.assets,{...valid.assets[0],id:fileId,kind:'font',name:'Fonte',mime:'font/ttf'}]};
 const parsed=manifestSchema.parse(theme);assert.equal(parsed.widgets[0].layers[0].x,.123);assert.equal(parsed.widgets[0].layers[0].weight,700);
 assert.equal(manifestSchema.safeParse({...theme,fonts:[]}).success,false);
 assert.equal(manifestSchema.safeParse({...theme,assets:valid.assets}).success,false);
 assert.equal(manifestSchema.safeParse({...theme,widgets:[{...theme.widgets[0],gridColumns:99}]}).success,false);
});
