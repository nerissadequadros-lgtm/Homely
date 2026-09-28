import sharp from 'sharp';
import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../assets/',import.meta.url));
const catalog=JSON.parse(await readFile(root+'catalog.json','utf8'));
const paths={phone:'M74 65C68 58 56 64 57 80C65 123 103 159 141 162C157 162 160 149 150 142L130 127L115 139C94 128 84 112 78 95L88 82Z',camera:'M62 83H90L99 68H132L143 83H163V159H62Z M138 119A26 26 0 1 0 86 119A26 26 0 1 0 138 119',chat:'M58 65H166V143H103L76 163V143H58Z M78 94H146 M78 114H127',browser:'M162 112A50 50 0 1 0 62 112A50 50 0 1 0 162 112 M62 112H162 M112 62C84 87 84 136 112 162C140 137 140 86 112 62'};
for(const [style,colors] of [['minimal',['#1A2133','#121722']],['color',['#98BAFF','#6753DE']],['3d',['#ECCEFF','#9E65E5']]]){
 for(const [name,path]of Object.entries(paths)){
  const svg='<svg xmlns="http://www.w3.org/2000/svg" width="224" height="224"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="'+colors[0]+'"/><stop offset="1" stop-color="'+colors[1]+'"/></linearGradient><filter id="shadow"><feDropShadow dx="0" dy="8" stdDeviation="5" flood-color="#482179" flood-opacity=".5"/></filter></defs><rect x="3" y="3" width="218" height="218" rx="58" fill="url(#g)" stroke="#FFFFFF" stroke-opacity=".16" stroke-width="2"/><path d="'+path+'" fill="none" stroke="#FAF8FF" stroke-width="'+(style==='minimal'?6:9)+'" stroke-linecap="round" stroke-linejoin="round" '+(style==='3d'?'filter="url(#shadow)"':'')+'/></svg>';
  await sharp(Buffer.from(svg)).png().toFile(root+'refined-'+style+'-'+name+'.png');
 }
}
for(const [index,entry] of catalog.entries()){
 const base64=(await readFile(root+entry.file)).toString('base64');
 entry.refinedStyle=index%3;
 entry.screenshots=[];
 for(const mode of ['lock','home']){
  let ui='<text x="80" y="95" font-family="Arial" font-size="29" fill="white">9:41</text><path d="M870 65h14v25h-14m22-35h14v35h-14m22-45h14v45h-14" fill="white" opacity=".8"/>';
  ui+='<text x="540" y="455" text-anchor="middle" font-family="'+(mode==='home'?'Georgia':'Arial')+'" font-weight="300" font-size="180" letter-spacing="-8" fill="white">10:24</text><text x="540" y="525" text-anchor="middle" font-family="Arial" font-size="30" fill="white" opacity=".8">segunda, 21 de setembro</text>';
  if(mode==='home'){
   ui+='<rect x="115" y="670" width="850" height="270" rx="48" fill="#131724" opacity=".48"/><text x="175" y="755" fill="#DED5FF" font-family="Arial" font-size="26">HOJE, NO SEU RITMO</text><text x="175" y="848" fill="white" font-family="Arial" font-size="58">Um novo começo.</text>';
   for(const [i,name]of Object.keys(paths).entries()){
    const icon=await readFile(root+'refined-'+['minimal','color','3d'][entry.refinedStyle]+'-'+name+'.png');
    ui+='<image x="'+(148+i*218)+'" y="1430" width="130" height="130" href="data:image/png;base64,'+icon.toString('base64')+'"/>';
   }
  }
  ui+='<rect x="405" y="1820" width="270" height="10" rx="5" fill="white" opacity=".8"/>';
  const svg='<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920"><image width="1080" height="1920" href="data:image/png;base64,'+base64+'"/><rect width="1080" height="1920" fill="#060911" opacity=".08"/>'+ui+'</svg>';
  const file=entry.file.replace('.png','-preview-'+mode+'.webp');
  await sharp(Buffer.from(svg)).webp({quality:85}).toFile(root+file);
  entry.screenshots.push(file);
 }
}
await writeFile(root+'catalog.json',JSON.stringify(catalog,null,2));
console.log('32 theme presentation screenshots and 12 app icons ready.');
