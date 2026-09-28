import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
test('real PostgreSQL API: accounts, ownership, media, social actions and archive', {skip:!process.env.TEST_DATABASE_URL},async(t)=>{
 process.env.DATABASE_URL=process.env.TEST_DATABASE_URL;process.env.NODE_ENV='test';process.env.STORAGE_DIR=await mkdtemp(join(tmpdir(),'homely-test-'));
 const {createApp}=await import('../src/app.js');
 const {pool}=await import('../src/db.js');
 const app=await createApp();const accounts:any[]=[];
 const send=(method:any,url:string,payload?:any,token?:string)=>app.inject({method,url,payload,headers:token?{authorization:'Bearer '+token}:{}});
 try {
  assert.equal((await send('GET','/health')).statusCode,200);
  assert.equal((await send('POST','/v1/themes',{})).statusCode,401);
  const suffix=randomUUID().slice(0,8);
  for(const name of ['alice','bob']){const r=await send('POST','/v1/auth/register',{email:name+suffix+'@example.com',password:'long-test-password!',username:name+suffix,displayName:name});assert.equal(r.statusCode,201,r.body);accounts.push(r.json());}
  const [a,b]=accounts;
  await t.test('username availability distinguishes free, taken and own handle',async()=>{assert.equal((await send('GET','/v1/usernames/availability?username=free'+suffix)).json().available,true);assert.equal((await send('GET','/v1/usernames/availability?username=alice'+suffix)).json().available,false);assert.equal((await send('GET','/v1/usernames/availability?username=alice'+suffix,undefined,a.token)).json().available,true);assert.equal((await send('GET','/v1/usernames/availability?username=a')).statusCode,400);});
  await t.test('unique usernames and authentication',async()=>{
   const r=await send('POST','/v1/auth/register',{email:'other'+suffix+'@example.com',password:'long-test-password!',username:'alice'+suffix,displayName:'A'});assert.equal(r.statusCode,409);
   assert.equal((await send('POST','/v1/auth/login',{email:'alice'+suffix+'@example.com',password:'wrong'})).statusCode,401);
  });
  const boundary='homely-test-boundary';
  const image=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+XcgAAAABJRU5ErkJggg==','base64');
  const payload=Buffer.concat([Buffer.from('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="test.png"\r\nContent-Type: image/png\r\n\r\n'),image,Buffer.from('\r\n--'+boundary+'--\r\n')]);
  const uploaded=await app.inject({method:'POST',url:'/v1/media',headers:{authorization:'Bearer '+a.token,'content-type':'multipart/form-data; boundary='+boundary},payload});
  assert.equal(uploaded.statusCode,201,uploaded.body);
  const media=uploaded.json();
  const extra=async()=>{const r=await app.inject({method:'POST',url:'/v1/media',headers:{authorization:'Bearer '+a.token,'content-type':'multipart/form-data; boundary='+boundary},payload});assert.equal(r.statusCode,201);return r.json();};
  await t.test('profile photo and cover require owned image uploads',async()=>{
   const profile={username:a.user.username,displayName:'Alice',bio:'Studio',location:'',website:'',avatarUrl:media.url,bannerUrl:media.url};
   const result=await send('PUT','/v1/me',profile,a.token);assert.equal(result.statusCode,200,result.body);assert.equal(result.json().avatarUrl,media.url);assert.equal(result.json().bannerUrl,media.url);
   assert.equal((await send('PUT','/v1/me',{...profile,username:b.user.username},b.token)).statusCode,400);
  });
  const icon=await extra(),screenshot=await extra();
  const manifest={schemaVersion:'1.2',widgets:[{id:randomUUID(),name:'Relógio',kind:'clock',text:'',foreground:'#FFFFFF',background:'#171625',accent:'#AEA2FF',radius:24,height:150,fontSize:40}],layout:{columns:4,iconSize:54,showLabels:true},title:'Integration theme',description:'Test content',category:'Minimal',tags:['test'],palette:{accent:'#7965FF',background:'#090C12',surface:'#171C27'},iconStyle:'circle',clockStyle:'thin',assets:[{...media,kind:'wallpaper',name:'Test'},{...icon,kind:'icon',name:'Phone',packageName:'com.android.dialer',category:'Comunicação',style:'3D'},{...screenshot,kind:'screenshot',name:'Home'}],compatibility:{minAndroid:26,launcherRequiredForIconPack:true,systemBars:'preview-only'},license:'CC0-1.0'};
  await t.test('publishing checks media ownership and exact paths',async()=>{
   assert.equal((await send('POST','/v1/themes',manifest,b.token)).statusCode,400);
   assert.equal((await send('POST','/v1/themes',{...manifest,assets:[{...manifest.assets[0],url:'/etc/passwd'}]},a.token)).statusCode,400);
  });
  const published=await send('POST','/v1/themes',manifest,a.token);assert.equal(published.statusCode,201,published.body);const theme=published.json();
  await t.test('builder navigation persists through publication and retrieval',async()=>{
   const layout={...manifest.layout,drawerColumns:5,drawerRows:4,drawerSpacing:8,drawerMargin:20,drawerIconSize:48,drawerMode:'pages',drawerDirection:'horizontal',drawerAnimation:'slide',animationDuration:360,reduceMotion:true,drawerOpacity:.72,drawerBlur:10,drawerRadius:30,drawerShowSearch:false,hapticFeedback:false,gestures:{swipeUp:'drawer',swipeDown:'wallpaper',swipeLeft:'next_page',swipeRight:'previous_page',tap:'app',doubleTap:'search',longPress:'widgets',pinchIn:'personalize',pinchOut:'none',drawerSwipeDown:'close_drawer',iconLongPress:'menu',quickPackage:'com.android.dialer'}};
   const saved=await send('POST','/v1/themes',{...manifest,schemaVersion:'1.5',title:'Builder navigation',layout},a.token);assert.equal(saved.statusCode,201,saved.body);
   const loaded=(await send('GET','/v1/themes/'+saved.json().id)).json();
   for(const [key,value] of Object.entries(layout))assert.deepEqual(loaded.manifest.layout[key],value,key);
   const archive=await send('GET','/v1/themes/'+saved.json().id+'/download');assert.equal(archive.statusCode,200);assert.equal(archive.rawPayload.subarray(0,2).toString(),'PK');
  });
  await t.test('studio themes publish editable icon sources and image layers',async()=>{
   const source=await extra();const layered={...manifest,schemaVersion:'1.3',title:'Studio layered',widgets:[{...manifest.widgets[0],kind:'custom',columns:4,rows:2,layers:[{id:randomUUID(),type:'image',x:0,y:0,width:.5,height:1,text:'',color:'#FFFFFF',fontSize:16,zIndex:0,visible:true,locked:false,fontFamily:'sans',imageAssetId:screenshot.id}]}],assets:[manifest.assets[0],{...manifest.assets[1],iconEditor:{sourceAssetId:source.id,background:'#171625',scale:.6,shadowRadius:2,strokeWidth:0,mask:'hexagon',rotation:0,mirrored:false}},{...screenshot,kind:'widget_image',name:'Photo'},{...source,kind:'icon_source',name:'Original',packageName:'com.android.dialer'}]};
   const saved=await send('POST','/v1/themes',layered,a.token);assert.equal(saved.statusCode,201,saved.body);
   const loaded=(await send('GET','/v1/themes/'+saved.json().id)).json();assert.equal(loaded.manifest.widgets[0].layers[0].imageAssetId,screenshot.id);assert.equal(loaded.manifest.assets[1].iconEditor.sourceAssetId,source.id);
   assert.equal((await send('GET','/v1/themes/'+saved.json().id+'/download')).statusCode,200);
  });
  await t.test('fonts remain downloadable with their detected MIME and cities are searchable',async()=>{
   const fontPayload=Buffer.concat([Buffer.from('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="test.ttf"\r\nContent-Type: font/ttf\r\n\r\n'),Buffer.from([0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0]),Buffer.from('\r\n--'+boundary+'--\r\n')]);
   const upload=await app.inject({method:'POST',url:'/v1/media',headers:{authorization:'Bearer '+a.token,'content-type':'multipart/form-data; boundary='+boundary},payload:fontPayload});assert.equal(upload.statusCode,201,upload.body);const file=upload.json();const response=await send('GET',file.url);assert.equal(response.statusCode,200);assert.match(response.headers['content-type'] as string,/font\/ttf/);
   const cities=await send('GET','/v1/weather/cities?q=Ponta%20Grossa');assert.equal(cities.statusCode,200);assert.equal(cities.json().items[0].country,'BR');assert.equal((await send('GET','/v1/weather?lat=999&lon=0')).statusCode,400);
  });
  await t.test('icon catalog excludes other assets and filters style',async()=>{ const all=(await send('GET','/v1/icons')).json();assert.equal(all.items.length,1);assert.equal(all.items[0].asset.kind,'icon');assert.equal((await send('GET','/v1/icons?style=3D')).json().items.length,1);assert.equal((await send('GET','/v1/icons?style=Pastel')).json().items.length,0);assert.equal((await send('GET','/v1/themes/'+theme.id)).json().manifest.widgets.length,1);assert.equal((await send('GET','/v1/themes/'+theme.id)).json().manifest.assets.filter((x:any)=>x.kind==='screenshot').length,1); });
  await t.test('likes and follows are idempotent',async()=>{
   for(let i=0;i<2;i++){assert.equal((await send('PUT','/v1/themes/'+theme.id+'/like',{active:true},b.token)).statusCode,200);assert.equal((await send('PUT','/v1/users/'+a.user.id+'/follow',{active:true},b.token)).statusCode,200);}
   assert.equal((await send('GET','/v1/themes/'+theme.id,undefined,b.token)).json().likes,1);
   assert.equal((await send('GET','/v1/users/'+a.user.id,undefined,b.token)).json().followers,1);
  });
  await t.test('private collection ownership and archive output',async()=>{
   const collection=(await send('GET','/v1/collections',undefined,a.token)).json().items[0];
   assert.equal((await send('PUT','/v1/themes/'+theme.id+'/save',{active:true,collectionId:collection.id},b.token)).statusCode,404);
   assert.equal((await send('PUT','/v1/themes/'+theme.id+'/save',{active:true},b.token)).statusCode,200);
   assert.equal((await send('GET','/v1/themes?saved=true',undefined,b.token)).json().items.length,1);
   const archive=await send('GET','/v1/themes/'+theme.id+'/download');assert.equal(archive.statusCode,200);assert.equal(archive.rawPayload.subarray(0,2).toString(),'PK');
   assert.equal((await send('DELETE','/v1/themes/'+theme.id,undefined,b.token)).statusCode,404);
  });
  await t.test('behavior is idempotent and daily discovery is private, scheduled and readable',async()=>{
    assert.equal((await send('POST','/v1/behavior',{events:[]})).statusCode,401);
    const event={id:randomUUID(),kind:'view',themeId:theme.id};
    for(let i=0;i<2;i++)assert.equal((await send('POST','/v1/behavior',{events:[event]},b.token)).statusCode,200);
    const counts=await pool.query('SELECT count(*)::int AS n FROM behavior_events WHERE id=$1',[event.id]);assert.equal(counts.rows[0].n,1);
    const profile=(await pool.query('SELECT profile FROM discovery_preferences WHERE user_id=$1',[b.user.id])).rows[0].profile;assert.ok(profile.weights['category:Minimal']>0);
    assert.equal((await send('PUT','/v1/discovery/preferences',{timezone:'invalid/zone',enabled:true},b.token)).statusCode,400);
    for(let i=0;i<2;i++)assert.equal((await send('PUT','/v1/discovery/preferences',{timezone:'America/Sao_Paulo',enabled:true},b.token)).statusCode,200);
    const notices=(await pool.query('SELECT * FROM discovery_notifications WHERE user_id=$1',[b.user.id])).rows;assert.equal(notices.length,1);assert.ok(notices[0].minute>=480&&notices[0].minute<1380);
    await pool.query("UPDATE discovery_notifications SET available_at=now()-interval '1 minute' WHERE id=$1",[notices[0].id]);
    const inbox=await send('GET','/v1/notifications',undefined,b.token);assert.equal(inbox.statusCode,200,inbox.body);assert.equal(inbox.json().unread,1);
    assert.equal((await send('GET','/v1/notifications/'+notices[0].id+'/themes',undefined,a.token)).statusCode,404);
    const selected=await send('GET','/v1/notifications/'+notices[0].id+'/themes',undefined,b.token);assert.equal(selected.statusCode,200,selected.body);assert.ok(selected.json().items.length>0);
    assert.equal((await send('GET','/v1/notifications',undefined,b.token)).json().unread,0);
    assert.equal((await send('GET','/v1/recommendations',undefined,b.token)).statusCode,200);
  });
  await t.test('profile fields, reports and session invalidation',async()=>{
   assert.equal((await send('PUT','/v1/me',{username:'bob'+suffix,displayName:'Bob Updated',bio:'My bio',location:'Brasil',website:'https://example.com'},b.token)).statusCode,200);
   assert.equal((await send('POST','/v1/themes/'+theme.id+'/report',{reason:'Test report for moderation'},b.token)).statusCode,201);
   assert.equal((await send('POST','/v1/auth/logout',{},b.token)).statusCode,200);
   assert.equal((await send('GET','/v1/me',undefined,b.token)).statusCode,401);
  });
 }finally{for(const a of accounts)await pool.query('DELETE FROM users WHERE id=$1',[a.user.id]);await app.close();await pool.end();await rm(process.env.STORAGE_DIR!,{recursive:true,force:true});}
});
