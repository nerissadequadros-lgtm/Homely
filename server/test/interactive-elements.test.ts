import { test } from 'node:test';
import assert from 'node:assert/strict';
import { manifestSchema, widgetSchema } from '../src/manifest.js';
import { randomUUID } from 'node:crypto';
const imageId=randomUUID(), assetId=randomUUID();
const state={id:randomUUID(),name:'Baixa',condition:'low',threshold:20,imageId,imageAssetId:assetId,color:'#ED8CAA',action:'toggle_state',animation:'fade'};
const layer={id:randomUUID(),type:'ring',x:0,y:0,width:1,height:1,text:'',color:'#FFFFFF',fontSize:20,zIndex:0,visible:true,locked:false,fontFamily:'sans',dataSource:'battery',states:[state],touchZones:[{id:randomUUID(),name:'Volume',x:0,y:0,width:.5,height:1,action:'volume_up'}]};
const widget={id:randomUUID(),name:'Meu painel',kind:'custom',foreground:'#FFFFFF',background:'#171625',accent:'#7965FF',radius:24,height:180,fontSize:24,layers:[layer],backgroundImageId:randomUUID(),backgroundImageAssetId:assetId,backgroundZoom:2,backgroundOpacity:.5};
test('independent state properties, touch zones and image framing survive JSON',()=>{
  const saved=widgetSchema.parse(widget);
  assert.deepEqual(widgetSchema.parse(JSON.parse(JSON.stringify(saved))),saved);
  assert.equal(saved.layers[0].states[0].imageAssetId,assetId);
  assert.equal(saved.layers[0].touchZones[0].action,'volume_up');
  assert.equal(saved.backgroundZoom,2);
});
test('element configuration rejects invalid zones, bounds and executable actions',()=>{
  for(const change of [{ringWidth:0},{animationDuration:1001},{tapAction:'shell'},{states:[{...state,threshold:101}]},{touchZones:[{...layer.touchZones[0],x:.8}]}])
    assert.equal(widgetSchema.safeParse({...widget,layers:[{...layer,...change}]}).success,false);
});
test('published state images require a real widget asset',()=>{
  const asset={id:assetId,kind:'widget_image',name:'State image',url:'/media/state.png',mime:'image/png',sha256:'a'.repeat(64),size:200};
  const base={schemaVersion:'1.5',title:'States',category:'Minimal',tags:[],palette:{accent:'#7965FF',background:'#090C12',surface:'#141922'},iconStyle:'rounded',clockStyle:'thin',widgets:[widget],assets:[asset,{...asset,id:randomUUID(),kind:"wallpaper"}],compatibility:{minAndroid:26,launcherRequiredForIconPack:true,systemBars:'preview-only'},license:'CC-BY-4.0'};
  assert.equal(manifestSchema.safeParse(base).success,true,JSON.stringify(manifestSchema.safeParse(base)));
  assert.equal(manifestSchema.safeParse({...base,assets:[]}).success,false);
  assert.equal(manifestSchema.safeParse({...base,widgets:[{...widget,layers:[{...layer,states:[state,state]}]}]}).success,false);
});

test('free widget dimensions persist and reject unsafe bounds',()=>{
  const parsed=widgetSchema.parse({...widget,height:720,canvasWidth:96,followThemePalette:true});
  assert.equal(parsed.height,720);assert.equal(parsed.canvasWidth,96);assert.equal(parsed.followThemePalette,true);
  assert.equal(widgetSchema.safeParse({...widget,canvasWidth:801}).success,false);
  assert.equal(widgetSchema.safeParse({...widget,height:63}).success,false);
});

test('icon states must reference images belonging to the same app',()=>{
  const sourceId=randomUUID(), pressedId=randomUUID();
  const asset={id:sourceId,kind:'icon_source',name:'Icon',url:'/media/icon.png',mime:'image/png',sha256:'a'.repeat(64),size:200,packageName:'app.example'};
  const editor={sourceAssetId:sourceId,stateAssetIds:{pressed:pressedId},scale:1,shadowRadius:0,strokeWidth:0,mask:'squircle',rotation:0,mirrored:false};
  const icon={...asset,id:randomUUID(),kind:'icon',iconEditor:editor};
  const wallpaper={...asset,id:randomUUID(),kind:'wallpaper',packageName:undefined};
  const base={schemaVersion:'1.5',title:'Icon states',category:'Minimal',tags:[],palette:{accent:'#7965FF',background:'#090C12',surface:'#141922'},iconStyle:'rounded',clockStyle:'thin',assets:[asset,wallpaper,icon,{...asset,id:pressedId}],compatibility:{minAndroid:26,launcherRequiredForIconPack:true,systemBars:'preview-only'},license:'CC-BY-4.0'};
  assert.equal(manifestSchema.safeParse(base).success,true);
  assert.equal(manifestSchema.safeParse({...base,assets:base.assets.filter(a=>a.id!==pressedId)}).success,false);
  assert.equal(manifestSchema.safeParse({...base,assets:base.assets.map(a=>a.id===pressedId?{...a,packageName:'app.other'}:a)}).success,false);
});
