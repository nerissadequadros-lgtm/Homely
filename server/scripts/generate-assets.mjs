import sharp from 'sharp';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../assets/',import.meta.url));await mkdir(root,{recursive:true});
const seeds=[
 ['midnight','Midnight','Natureza','#788FD6',['#111E39','#60527B','#F1A896'],0,true],
 ['aurora','Aurora','Natureza','#8FBDE5',['#050E29','#173E60','#4FBAAE'],1,true],
 ['silk','Violet silk','Abstrato','#A28BFF',['#080817','#2E185C','#BC8AE8'],2,true],
 ['moss','Moss','Natureza','#83B08D',['#061613','#194437','#82AF7D'],3,false],
 ['dune','Dune','Minimal','#E1B990',['#29231F','#91725C','#E9C9A1'],4,true],
 ['cosmic','Cosmic','Espaço','#A99AFF',['#06091E','#251C4D','#7760BF'],5,true],
 ['bloom','Bloom','Abstrato','#F4AEBE',['#4E263F','#C87891','#FFC6C4'],2,false],
 ['tide','Tide','Natureza','#6BD4D5',['#063241','#148A98','#AEDED0'],6,false],
 ['alpine','Alpine','Natureza','#A6BFCE',['#334F69','#ACC1CB','#EDD4C2'],0,false],
 ['noir','Noir','Minimal','#B4BBCA',['#080B11','#252D3D','#647188'],4,true],
 ['ember','Ember','Abstrato','#F59576',['#130D17','#6B2236','#EE8162'],2,false],
 ['moonrise','Moonrise','Espaço','#D4C4B5',['#0F182B','#445266','#C2AA91'],5,false],
 ['forest','Forest whispers','Natureza','#99BFB1',['#18342F','#638578','#D2DBBA'],0,false],
 ['neon','After hours','Tecnologia','#BD96F8',['#0A0927','#30247C','#E473CC'],7,true],
 ['peach','Peach hour','Minimal','#F8B29A',['#784E7C','#E09191','#FFCEAD'],4,false],
 ['glacier','Glacier','Natureza','#92CBE5',['#18364D','#648FA5','#D9ECE8'],0,false]
];
function stars(seed){let out='';for(let i=0;i<150;i++){const x=(i*397+seed*113)%1080,y=(i*i*47+seed*211)%1500,r=i%7===0?1.9:.8;out+='<circle cx="'+x+'" cy="'+y+'" r="'+r+'" fill="#ffffff" opacity="'+(.15+(i%6)/10)+'"/>';}return out;}
function landscape(colors,variant,index){
 const [dark,mid,light]=colors;let shapes='';
 const mountain='<path d="M-60 1270 L90 1080 220 1165 420 865 515 1065 650 905 835 1190 980 1040 1130 1280 V1980 H-60Z" fill="'+mid+'"/><path d="M310 1030 L420 865 515 1065 461 1028 431 971 402 995 375 978Z" fill="'+light+'" opacity=".58"/>';
 if(variant===0||variant===1){
  shapes=stars(index);
  if(variant===1)shapes+='<g filter="url(#blur)" opacity=".65"><path d="M120 -100 Q880 540 280 1110 Q1110 700 950 -100" fill="none" stroke="#72FBCB" stroke-width="60"/><path d="M100 -150 Q580 620 140 1120" fill="none" stroke="#9477ED" stroke-width="110"/></g>';
  shapes+='<circle cx="798" cy="547" r="62" fill="'+light+'" opacity=".77"/>'+mountain;
  shapes+='<path d="M0 1390L200 1290 400 1420 670 1105 885 1360 1080 1180V1920H0Z" fill="'+dark+'" opacity=".82"/>';
  for(let j=0;j<34;j++){let x=(j*109)%1160-40,y=1210+(j*83)%700,h=140+(j*71)%410;shapes+='<path d="M'+x+' '+(y-h)+' l'+(-h*.16)+' '+(h*.42)+' h'+(h*.09)+' l'+(-h*.14)+' '+(h*.28)+' h'+(h*.1)+' l'+(-h*.15)+' '+(h*.3)+' h'+(h*.52)+' l'+(-h*.15)+' '+(-h*.3)+' h'+(h*.1)+' l'+(-h*.14)+' '+(-h*.28)+' h'+(h*.09)+'Z" fill="'+dark+'" opacity="'+(.5+j%4*.15)+'"/>';}
 }else if(variant===2){
  shapes='<g transform="rotate(-25 540 960)">';
  for(let j=0;j<6;j++)shapes+='<path d="M-300 '+(450+j*220)+' C300 '+(80+j*220)+' 550 '+(1320+j*50)+' 1500 '+(690+j*200)+' L1500 2500H-300Z" fill="url(#wave)" stroke="'+light+'" stroke-opacity=".3" stroke-width="2"/>';
  shapes+='</g>';
 }else if(variant===3){
  for(let j=0;j<25;j++){let x=(j*271)%1300-100,y=(j*389)%2400-200;shapes+='<g transform="translate('+x+' '+y+') rotate('+(j*73%180-90)+')"><path d="M0 320 C-430 200 -280 -200 0 -390 C300 -220 350 180 0 320Z" fill="url(#leaf)" stroke="'+light+'" stroke-opacity=".1" stroke-width="2"/><path d="M0 300 L0 -360 M0 160L-190 -15M0 50L185 -95M0 -60L-140 -180M0 -150L100 -240" fill="none" stroke="'+light+'" opacity=".25" stroke-width="3"/></g>';}
 }else if(variant===4){
  shapes='<circle cx="760" cy="620" r="95" fill="'+light+'" opacity=".65"/>';
  for(let j=0;j<8;j++)shapes+='<path d="M-200 '+(970+j*138)+' Q'+(300+j*90)+' '+(450+j*200)+' 1300 '+(1210+j*140)+' V2300H-200Z" fill="url(#wave)" stroke="'+light+'" stroke-opacity=".35" stroke-width="1.5"/>';
 }else if(variant===5){
  shapes=stars(index)+'<circle cx="555" cy="935" r="340" fill="url(#planet)"/><ellipse cx="555" cy="955" rx="560" ry="105" fill="none" stroke="'+light+'" stroke-width="22" opacity=".3" transform="rotate(-28 555 955)"/><ellipse cx="555" cy="955" rx="540" ry="100" fill="none" stroke="'+light+'" stroke-width="2" opacity=".8" transform="rotate(-28 555 955)"/>';
 }else if(variant===6){
  for(let j=0;j<18;j++)shapes+='<path d="M-50 '+(j*150)+' Q180 '+(j*150-100)+' 530 '+(j*150+60)+'T1150 '+(j*150-50)+'" fill="none" stroke="'+light+'" stroke-opacity="'+(.07+j%4*.05)+'" stroke-width="'+(10+j%3*8)+'"/>';
  shapes+='<path d="M-50 1740Q550 1280 1150 1390V2000H-50Z" fill="#E9D0AF"/><path d="M-50 1710Q550 1250 1150 1360" fill="none" stroke="#FFFFFF" stroke-width="25" opacity=".8"/>';
 }else{
  shapes=stars(index);for(let j=0;j<12;j++){const left=j<6,x=left?j*78:700+(j-6)*80,w=40+(j%3)*15,h=350+j%4*160;shapes+='<rect x="'+x+'" y="'+(1050-h)+'" width="'+w+'" height="'+(h+870)+'" fill="'+dark+'" stroke="'+(j%2===0?'#8B68EE':'#D25BA6')+'" stroke-width="3"/>';for(let k=0;k<14;k++)shapes+='<rect x="'+(x+10)+'" y="'+(1090-h+k*65)+'" width="9" height="22" fill="'+(j%2===0?'#8B68EE':'#D25BA6')+'" opacity=".65"/>';}
  shapes+='<path d="M350 1100L0 1920H1080L690 1100Z" fill="url(#wave)"/><path d="M520 1160L410 1920M590 1160L690 1920" stroke="#DDA4F5" stroke-width="4" opacity=".6"/>';
 }
 return '<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920"><defs><linearGradient id="sky" x2=".1" y2="1"><stop stop-color="'+dark+'"/><stop offset=".6" stop-color="'+mid+'"/><stop offset="1" stop-color="'+light+'"/></linearGradient><linearGradient id="wave" x1="0" y1="0" x2=".6" y2="1"><stop stop-color="'+light+'" stop-opacity=".8"/><stop offset=".17" stop-color="'+mid+'"/><stop offset="1" stop-color="'+dark+'"/></linearGradient><linearGradient id="leaf" x2="1" y2=".5"><stop stop-color="'+dark+'"/><stop offset=".55" stop-color="'+mid+'"/><stop offset="1" stop-color="'+dark+'"/></linearGradient><radialGradient id="planet" cx=".28" cy=".25" r=".8"><stop stop-color="'+light+'"/><stop offset=".55" stop-color="'+mid+'"/><stop offset="1" stop-color="'+dark+'"/></radialGradient><filter id="blur"><feGaussianBlur stdDeviation="35"/></filter><filter id="noise"><feTurbulence type="fractalNoise" baseFrequency=".65" numOctaves="3" stitchTiles="stitch"/><feColorMatrix type="saturate" values="0"/></filter></defs><rect width="1080" height="1920" fill="url(#sky)"/>'+shapes+'<rect width="1080" height="1920" filter="url(#noise)" opacity=".045"/></svg>';
}
const catalog=[];
for(const [slug,title,category,accent,colors,variant,icons] of seeds){
 await sharp(Buffer.from(landscape(colors,variant,catalog.length))).png().toFile(root+slug+'.png');
 catalog.push({file:slug+'.png',title,category,accent,icons,tags:[category.toLowerCase(),slug,'original'],description:'Coleção original Homely. '+({Natureza:'Paisagens para desacelerar.',Abstrato:'Cor, forma e um novo olhar.',Minimal:'Menos ruído. Mais você.',Espaço:'Um universo na sua tela.',Tecnologia:'Luzes que contam outra história.'}[category]||'')+' Arte procedural original, disponível em CC0 para você personalizar e compartilhar.'});
}
const paths={
 phone:'M74 65C68 58 56 64 57 80C65 123 103 159 141 162C157 162 160 149 150 142L130 127L115 139C94 128 84 112 78 95L88 82Z',
 camera:'M62 83H90L99 68H132L143 83H163V159H62Z M138 119A26 26 0 1 0 86 119A26 26 0 1 0 138 119',
 chat:'M58 65H166V143H103L76 163V143H58Z M78 94H146 M78 114H127',
 browser:'M162 112A50 50 0 1 0 62 112A50 50 0 1 0 162 112 M62 112H162 M112 62C84 87 84 136 112 162C140 137 140 86 112 62'
};
for(const [name,path]of Object.entries(paths)){
 const svg='<svg xmlns="http://www.w3.org/2000/svg" width="224" height="224"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="#B8A2FD"/><stop offset="1" stop-color="#6547CC"/></linearGradient></defs><rect width="224" height="224" rx="62" fill="url(#g)"/><path d="'+path+'" fill="none" stroke="#FAF6FF" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/></svg>';
 await sharp(Buffer.from(svg)).png().toFile(root+'icon-'+name+'.png');
}
await writeFile(root+'catalog.json',JSON.stringify(catalog,null,2));
const brand='<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="1000"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="#B7A4FF"/><stop offset="1" stop-color="#7965FF"/></linearGradient></defs><rect width="1440" height="1000" fill="#090C12"/><text x="90" y="95" fill="#AEA2FF" font-family="Arial" font-size="16" letter-spacing="5">HOMELY / IDENTIDADE 01</text><rect x="90" y="170" width="196" height="196" rx="54" fill="#1C1639"/><path d="M132 250L188 204L244 250V315H207M169 315H132V250" fill="none" stroke="url(#g)" stroke-width="13" stroke-linejoin="round" stroke-linecap="round"/><text x="328" y="295" font-family="Arial" font-size="108" font-weight="bold" letter-spacing="-5" fill="#F5F4FA">Homely</text><text x="94" y="460" font-family="Arial" font-size="47" fill="#F5F4FA">Seu celular. Seu universo.</text><text x="94" y="518" font-family="Arial" font-size="23" fill="#9298A8">Descubra. Crie. Faça do seu jeito.</text><line x1="90" y1="587" x2="1350" y2="587" stroke="#252B38"/>'+[['#7965FF','Violeta Homely'],['#AEA2FF','Lilás'],['#090C12','Noite'],['#171C27','Ardósia'],['#F8F8FC','Nuvem']].map(([hex,label],i)=>'<rect x="'+(90+i*252)+'" y="640" width="222" height="120" rx="20" fill="'+hex+'" stroke="#303646"/><text x="'+(90+i*252)+'" y="800" font-family="Arial" font-size="20" fill="#F5F4FA">'+label+'</text><text x="'+(90+i*252)+'" y="830" font-family="Arial" font-size="16" fill="#9298A8">'+hex+'</text>').join('')+'<text x="90" y="932" fill="#9298A8" font-family="Arial" font-size="16">ANDROID NATIVO · ÍCONE ADAPTATIVO · CLARO + ESCURO · COMUNIDADE CRIATIVA</text></svg>';
await writeFile(new URL('../../brand/identity.svg',import.meta.url),brand);
await sharp(Buffer.from(brand)).png().toFile(fileURLToPath(new URL('../../brand/identity.png',import.meta.url)));
const logo='<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512"><rect width="512" height="512" rx="136" fill="#100D26"/><path d="M125 235L256 124L387 235V391H301M211 391H125V235" fill="none" stroke="#A99AFF" stroke-width="30" stroke-linecap="round" stroke-linejoin="round"/></svg>';
await writeFile(new URL('../../brand/homely-icon.svg',import.meta.url),logo);await sharp(Buffer.from(logo)).png().toFile(fileURLToPath(new URL('../../brand/homely-icon-512.png',import.meta.url)));
console.log('16 original wallpapers, icon pack and brand assets generated.');
