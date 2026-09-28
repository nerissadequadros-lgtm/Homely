import { z } from 'zod';
import { iconCategories, iconStyles } from './catalog.js';
export const categories = ['Minimal','Natureza','Abstrato','Espaço','Anime','Tecnologia'] as const;
const hex=z.string().regex(/^#[0-9a-fA-F]{6}$/);
const pkg=z.string().regex(/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/);
const masks=z.enum(['rounded','circle','squircle','teardrop','hexagon','pentagon','none']);
const font=z.enum(['sans','serif','mono','display','hand']);
const iconEditor=z.object({sourceAssetId:z.string().uuid(),stateAssetIds:z.record(z.enum(['default','pressed','disabled']),z.string().uuid()).default({}),background:hex.nullable().optional(),scale:z.number().min(.2).max(1.5),shadowColor:hex.nullable().optional(),shadowRadius:z.number().min(0).max(20),strokeColor:hex.nullable().optional(),strokeWidth:z.number().min(0).max(10),mask:masks,rotation:z.number().min(0).max(360),mirrored:z.boolean(),offsetX:z.number().min(-54).max(54).default(0),offsetY:z.number().min(-54).max(54).default(0),placement:z.enum(['grid','free']).default('free'),gridColumns:z.number().int().min(2).max(12).default(4),text:z.string().max(40).default(''),textColor:hex.default('#FFFFFF'),textSize:z.number().int().min(8).max(40).default(16),fontId:z.string().uuid().nullable().optional(),fontFamily:font.default('sans')}).strict();
const asset=z.object({id:z.string().uuid(),kind:z.enum(['wallpaper','live_wallpaper','icon','icon_source','screenshot','widget_image','font']),name:z.string().min(1).max(80),url:z.string().max(300),mime:z.enum(['image/jpeg','image/png','image/webp','video/mp4','font/ttf','font/otf']),sha256:z.string().regex(/^[a-f0-9]{64}$/),size:z.number().int().positive().max(50*1024*1024),packageName:pkg.optional(),category:z.enum(iconCategories).optional(),style:z.enum(iconStyles).optional(),iconEditor:iconEditor.nullable().optional()}).strict();
const weather=z.object({city:z.string().max(120).default(''),latitude:z.number().min(-90).max(90).nullable().default(null),longitude:z.number().min(-180).max(180).nullable().default(null),unit:z.enum(['c','f']).default('c'),style:z.enum(['editorial','compact','forecast']).default('editorial'),showCity:z.boolean().default(true),showCondition:z.boolean().default(true),showRange:z.boolean().default(true),showHumidity:z.boolean().default(false),showWind:z.boolean().default(false),showIcon:z.boolean().default(true)}).strict().refine(w=>(w.latitude===null)===(w.longitude===null));
const clock=z.object({format:z.enum(['24','12']).default('24'),seconds:z.boolean().default(false),zone:z.string().max(80).default('').refine(v=>{try{if(v)new Intl.DateTimeFormat('en',{timeZone:v});return true}catch{return false}}),dateStyle:z.enum(['full','short','weekday']).default('full'),marks:z.boolean().default(true)}).strict();
const elementAction=z.enum(['auto','none','app','toggle_state','animate','wifi','bluetooth','flashlight','volume','volume_up','volume_down','settings','alarm','calendar','storage']);
const elementAnimation=z.enum(['none','fade','scale','slide','rotate']);
const elementState=z.object({id:z.string().uuid(),name:z.string().min(1).max(60),condition:z.enum(['always','pressed','touched','toggled','low','high','enabled','disabled','connected','disconnected','unavailable']),threshold:z.number().min(0).max(100).default(20),color:hex.nullable().optional(),background:hex.nullable().optional(),text:z.string().max(300).nullable().optional(),imageId:z.string().uuid().nullable().optional(),imageAssetId:z.string().uuid().nullable().optional(),opacity:z.number().min(0).max(1).default(1),scale:z.number().min(.5).max(1.5).default(1),rotation:z.number().min(-360).max(360).default(0),action:z.union([z.literal('inherit'),elementAction]).default('inherit'),animation:z.union([z.literal('inherit'),elementAnimation]).default('inherit'),components:z.array(z.record(z.unknown())).max(16).nullable().optional()}).strict().refine(s=>!s.imageAssetId||!!s.imageId);
const touchZone=z.object({id:z.string().uuid(),name:z.string().max(60),x:z.number().min(0).max(.95),y:z.number().min(0).max(.95),width:z.number().min(.05).max(1),height:z.number().min(.05).max(1),action:elementAction}).strict().refine(s=>s.x+s.width<=1.001&&s.y+s.height<=1.001);
const layer=z.object({id:z.string().uuid(),type:z.enum(['element','text','title','image','clock','analog','date','battery','battery_ring','shortcut','shape','note','spacer','progress','countdown','weather','calendar','phone','number','icon','ring','bar','indicator','ram','storage','wifi','bluetooth','lan','flashlight','volume','signal','cpu']),x:z.number().min(0).max(.95),y:z.number().min(0).max(.95),width:z.number().min(.05).max(1),height:z.number().min(.05).max(1),text:z.string().max(300),color:hex,fontSize:z.number().int().min(8).max(120),zIndex:z.number().int().min(0).max(31),visible:z.boolean(),locked:z.boolean(),imageAssetId:z.string().uuid().nullable().optional(),tapPackage:pkg.nullable().optional(),fontFamily:font,fontId:z.string().uuid().nullable().optional(),opacity:z.number().min(0).max(1).default(1),weight:z.number().int().min(100).max(900).default(400),alignment:z.enum(['start','center','end']).default('center'),progress:z.number().min(0).max(1).default(.5),targetTime:z.number().int().min(0).default(0),shape:z.enum(['rounded','circle','line']).default('rounded'),weather:weather.optional(),clock:clock.optional(),phoneNumber:z.string().max(30).default(''),tapAction:elementAction.default('auto'),monthOffset:z.number().int().min(-12).max(12).default(0),imageScale:z.enum(['crop','fit']).default('crop'),imageZoom:z.number().min(1).max(4).default(1),imageOffsetX:z.number().min(-1).max(1).default(0),imageOffsetY:z.number().min(-1).max(1).default(0),dataSource:z.enum(['auto','none','battery','ram','storage','wifi','bluetooth','lan','flashlight','volume','signal','cpu']).default('auto'),numberFormat:z.enum(['number','percent']).default('percent'),showValue:z.boolean().default(true),iconName:z.string().max(16).default('auto'),elementBackground:hex.nullable().optional(),elementRadius:z.number().int().min(0).max(100).default(8),elementBorderColor:hex.default('#FFFFFF'),elementBorderWidth:z.number().min(0).max(12).default(0),elementPadding:z.number().int().min(0).max(48).default(0),ringWidth:z.number().min(1).max(32).default(8),trackColor:hex.default('#FFFFFF'),trackOpacity:z.number().min(0).max(1).default(.15),animation:elementAnimation.default('none'),animationDuration:z.number().int().min(0).max(1000).default(220),longPressAction:elementAction.default('none'),states:z.array(elementState).max(12).default([]),touchZones:z.array(touchZone).max(8).default([]),components:z.array(z.record(z.unknown())).max(16).default([]),rotation:z.number().min(-360).max(360).default(0)}).strict().refine(l=>l.x+l.width<=1.001&&l.y+l.height<=1.001,'Camada fora do canvas.');
export const widgetSchema=z.object({id:z.string().uuid(),name:z.string().min(1).max(60),kind:z.enum(['custom','clock','date','battery','text','image']),text:z.string().max(300).default(''),foreground:hex,background:hex,accent:hex,radius:z.number().int().min(0).max(48),height:z.number().int().min(64).max(800),canvasWidth:z.number().int().min(64).max(800).default(320),followThemePalette:z.boolean().default(false),fontSize:z.number().int().min(8).max(120),imageAssetId:z.string().uuid().nullable().optional(),tapPackage:pkg.nullable().optional(),layers:z.array(layer).max(16).default([]),columns:z.union([z.literal(2),z.literal(4)]).default(4),rows:z.union([z.literal(2),z.literal(4)]).default(2),placement:z.enum(['grid','free']).default('free'),gridColumns:z.number().int().min(2).max(12).default(4),gridRows:z.number().int().min(2).max(12).default(4),backgroundImageId:z.string().uuid().nullable().optional(),backgroundImageAssetId:z.string().uuid().nullable().optional(),backgroundScale:z.enum(['crop','fit']).default('crop'),backgroundZoom:z.number().min(1).max(4).default(1),backgroundOffsetX:z.number().min(-1).max(1).default(0),backgroundOffsetY:z.number().min(-1).max(1).default(0),backgroundOpacity:z.number().min(0).max(1).default(1),backgroundOverlay:z.number().min(0).max(1).default(0)}).strict().refine(w=>!w.backgroundImageAssetId||!!w.backgroundImageId,'Identificador da imagem de fundo ausente.').refine(w=>(w.columns===4||w.rows===2)&&new Set(w.layers.map(l=>l.id)).size===w.layers.length,'Camadas ou dimensões inválidas.');
function compositionLayers(l:z.infer<typeof layer>):z.infer<typeof layer>[] {
 const children=[...l.components,...l.states.flatMap(s=>s.components??[])];
 return children.map(child=>{
   if((Array.isArray(child.components)&&child.components.length)||(Array.isArray(child.states)&&child.states.some((s:any)=>s.components?.length)))throw new Error('Composição aninhada não permitida.');
   return layer.parse(child);
 });
}
const gestureAction=z.enum(['drawer','close_drawer','search','next_page','previous_page','personalize','wallpaper','widgets','lock','app','none']);
const gestures=z.object({
 longPress:gestureAction.default('personalize'),
 iconLongPress:z.enum(['menu','edit','info','remove']).default('menu'),
 swipeUp:gestureAction.default('drawer'),
 swipeDown:z.union([gestureAction,z.literal('notifications')]).default('search'),
 swipeLeft:gestureAction.default('next_page'),
 swipeRight:gestureAction.default('previous_page'),
 tap:gestureAction.default('none'),
 doubleTap:gestureAction.default('none'),
 pinchIn:gestureAction.default('none'),
 pinchOut:gestureAction.default('none'),
 drawerSwipeDown:gestureAction.default('close_drawer'),
 quickPackage:pkg.nullable().optional()
}).strict();
const layout=z.object({
 dock:z.array(pkg).max(8).default(['com.samsung.android.dialer','com.samsung.android.messaging','com.android.chrome','com.sec.android.app.camera']),
 showSearchBar:z.boolean().default(true),
 columns:z.number().int().min(3).max(8),
 rows:z.number().int().min(4).max(12).default(6),
 iconSize:z.number().int().min(30).max(96),
 iconPadding:z.number().int().min(0).max(32).default(12),
 showLabels:z.boolean(),
 drawerColumns:z.number().int().min(3).max(8).default(4),
 drawerRows:z.number().int().min(2).max(10).default(5),
 drawerSpacing:z.number().int().min(0).max(32).default(12),
 drawerMargin:z.number().int().min(0).max(40).default(16),
 drawerIconSize:z.number().int().min(30).max(96).default(54),
 drawerMode:z.enum(['continuous','pages','list','custom']).default('continuous'),
 drawerDirection:z.enum(['horizontal','vertical']).default('horizontal'),
 drawerAnimation:z.enum(['slide','fade','zoom','none']).default('slide'),
 animationDuration:z.number().int().min(0).max(800).default(280),
 reduceMotion:z.boolean().default(false),
 drawerOpacity:z.number().min(.2).max(1).default(.94),
 drawerBlur:z.number().int().min(0).max(30).default(0),
 drawerRadius:z.number().int().min(0).max(48).default(24),
 drawerShowSearch:z.boolean().default(true),
 hapticFeedback:z.boolean().default(true),
 showDrawerButton:z.boolean().default(true),
 showDrawerHeader:z.boolean().default(true),
 showDrawerCloseButton:z.boolean().default(true),
 showPageIndicators:z.boolean().default(true),
 showNavigationHandle:z.boolean().default(true),
 immersiveHome:z.boolean().default(false),
 systemBarIcons:z.enum(['auto','light','dark']).default('light'),
 quickAction:z.enum(['studio','search','app','none']).default('studio'),
 gestures:gestures.default({}),
 kit:z.array(z.object({id:z.string().uuid(),kind:z.enum(['icon','widget']),reference:z.string().min(1).max(200),x:z.number().min(0).max(1),y:z.number().min(0).max(1),page:z.number().int().min(0).max(20),width:z.number().min(.05).max(1).default(.2),height:z.number().min(.05).max(1).default(.13)}).refine(i=>i.x+i.width<=1.001&&i.y+i.height<=1.001,'Item do kit fora da tela.')).max(120).default([]),
 drawerStyle:z.enum(['gesture','button']).default('gesture'),
 drawerLayout:z.enum(['grid','list']).default('grid'),
 folderStyle:z.enum(['window','fullscreen']).default('window'),
 appAnimation:z.enum(['zoom','fade','slide','none']).default('zoom'),
 scrollDirection:z.enum(['vertical','horizontal']).default('vertical'),
 statusBar:z.object({show:z.boolean(),batteryIcon:z.enum(['ios','android','hidden']),networkIcon:z.enum(['ios','android','hidden']),clockFormat:z.enum(['24h','12h','hidden']),tint:z.union([z.literal('auto'),hex]),background:z.union([z.literal('transparent'),hex]).default('transparent'),backgroundOpacity:z.number().min(0).max(1).default(1)}).strict().default({show:true,batteryIcon:'ios',networkIcon:'ios',clockFormat:'24h',tint:'auto'})
}).strict().refine(l=>
 (l.quickAction!=='app'&&!Object.entries(l.gestures).some(([key,value])=>key!=='quickPackage'&&value==='app'))||l.gestures.quickPackage!=null,
 'Escolha um aplicativo para a ação de abrir app.'
);
export const manifestSchema=z.object({
 schemaVersion:z.enum(['1.0','1.1','1.2','1.3','1.4','1.5']),title:z.string().trim().min(3).max(60),description:z.string().max(1000).default(''),category:z.enum(categories),tags:z.array(z.string().trim().min(1).max(24)).max(10),
 palette:z.object({accent:hex,background:hex,surface:hex,secondary:hex.default('#AEA2FF'),text:hex.default('#FFFFFF')}),iconStyle:masks,clockStyle:z.enum(['thin','bold','serif']),fontFamily:font.default('sans'),fonts:z.array(z.object({id:z.string().uuid(),name:z.string().min(1).max(60),assetId:z.string().uuid().nullable().optional()})).max(20).default([]),assets:z.array(asset).min(1).max(512),
 compatibility:z.object({minAndroid:z.number().int().min(26).max(36),launcherRequiredForIconPack:z.boolean(),systemBars:z.literal('preview-only')}),widgets:z.array(widgetSchema).max(12).default([]),layout:layout.default({columns:4,iconSize:54,showLabels:true}),license:z.enum(['CC-BY-4.0','CC0-1.0','personal-use'])
}).strict().superRefine((m,ctx)=>{
 const fail=(message:string)=>ctx.addIssue({code:'custom',message});
 if(!m.assets.some(a=>a.kind==='wallpaper'))fail('Inclua uma imagem de wallpaper para a prévia.');
 if(new Set(m.assets.map(a=>a.id)).size!==m.assets.length)fail('Recursos duplicados.');
 if(m.assets.filter(a=>a.kind==='screenshot').length>8)fail('Use até 8 screenshots.');
 if(m.schemaVersion==='1.0'&&m.assets.some(a=>a.kind==='screenshot'))fail('Screenshots exigem formato 1.1.');
 if(m.assets.reduce((sum,a)=>sum+a.size,0)>250*1024*1024)fail('O tema excede 250 MB.');
 if(new Set(m.widgets.map(w=>w.id)).size!==m.widgets.length)fail('Widgets duplicados.');
 if(!['1.2','1.3','1.4','1.5'].includes(m.schemaVersion)&&(m.widgets.length||m.assets.some(a=>a.kind==='widget_image')))fail('Widgets exigem formato 1.2.');
 if(!['1.3','1.4','1.5'].includes(m.schemaVersion)&&(m.widgets.some(w=>w.layers.length>0)||m.assets.some(a=>a.kind==='icon_source'||a.iconEditor)))fail('Camadas exigem formato 1.3.');
 if(m.fonts.length && !['1.4','1.5'].includes(m.schemaVersion))fail('Fontes exigem formato 1.4.');
 for(const f of m.fonts)if(!m.assets.some(a=>a.kind==='font'&&a.id===f.assetId))fail('Arquivo de fonte ausente.');
 for(const w of m.widgets)for(const l of w.layers.flatMap(l=>{try{return [l,...compositionLayers(l)]}catch{return [l]}}))if(l.fontId&&!m.fonts.some(f=>f.id===l.fontId))fail('Fonte da camada ausente.');
 for(const a of m.assets)if(a.iconEditor?.fontId&&!m.fonts.some(f=>f.id===a.iconEditor!.fontId))fail('Fonte do ícone ausente.');
 for(const a of m.assets)if(a.kind==='font'&&a.size>5*1024*1024)fail('Fonte acima de 5 MB.');
 if(new Set(m.fonts.map(f=>f.id)).size!==m.fonts.length)fail('Fontes duplicadas.');
 if(new Set(m.layout.kit.map(i=>i.id)).size!==m.layout.kit.length)fail('Itens do kit duplicados.');
 for(const item of m.layout.kit)if(item.kind==='widget'&&!m.widgets.some(w=>w.id===item.reference))fail('Widget do kit ausente.');
 for(const w of m.widgets) {
  const allLayers:z.infer<typeof layer>[]=[];
  for(const l of w.layers) {
   allLayers.push(l);try{allLayers.push(...compositionLayers(l));}catch{fail('Camadas da composição inválidas.');}
  }
  for(const l of allLayers) {
   if(new Set(l.states.map(s=>s.id)).size!==l.states.length||new Set(l.touchZones.map(s=>s.id)).size!==l.touchZones.length)fail('Estados ou áreas duplicados.');
   for(const s of l.states)if(s.imageId&&!m.assets.some(a=>a.id===s.imageAssetId&&a.kind==='widget_image'))fail('Imagem do estado ausente.');
  }
  if(w.backgroundImageId&&!m.assets.some(a=>a.id===w.backgroundImageAssetId&&a.kind==='widget_image'))fail('Imagem de fundo do widget ausente.');
  if(w.kind==='image'&&!m.assets.some(a=>a.id===w.imageAssetId&&a.kind==='widget_image'))fail('Imagem do widget ausente.');
  for(const l of allLayers)if(l.type==='image'&&!m.assets.some(a=>a.id===l.imageAssetId&&a.kind==='widget_image'))fail('Imagem da camada ausente.');
 }
 for(const a of m.assets) {
  for(const ref of Object.values(a.iconEditor?.stateAssetIds??{}))if(!m.assets.some(s=>s.id===ref&&s.kind==='icon_source'&&s.packageName===a.packageName))fail('Imagem do estado do ícone ausente ou incompatível.');
  if((a.kind==='font')!==(a.mime==='font/ttf'||a.mime==='font/otf'))fail('Tipo de fonte incompatível.');
  if((a.kind==='live_wallpaper')!==(a.mime==='video/mp4'))fail('Tipo de mídia incompatível.');
  if(['icon','icon_source'].includes(a.kind)&&!a.packageName)fail('Associe o ícone a um aplicativo.');
  if(a.iconEditor&&(a.kind!=='icon'||!m.assets.some(s=>s.kind==='icon_source'&&s.id===a.iconEditor!.sourceAssetId&&s.packageName===a.packageName)))fail('Camada de ícone ausente ou incompatível.');
 }
});
export type Manifest=z.infer<typeof manifestSchema>;
