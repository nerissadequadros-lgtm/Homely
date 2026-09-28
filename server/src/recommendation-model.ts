import { createHash } from 'node:crypto';

export type PreferenceProfile = { weights: Record<string, number>; seen: Record<string, number>; version: number };
export type Candidate = { id: string; category: string; tags: string[]; author_id?: string; author?: { id: string }; manifest: any; downloads: number; created_at?: Date|string; createdAt?: string; likes?: number };
export const emptyProfile = ():PreferenceProfile => ({weights:{},seen:{},version:1});
export function colorBucket(hex:string):string {
  if(!/^#[0-9a-f]{6}$/i.test(hex))return 'unknown';
  const channels=[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255);
  const [r,g,b]=channels,max=Math.max(...channels),min=Math.min(...channels),d=max-min;
  if(d<.1)return max<.25?'dark':max>.8?'light':'neutral';
  const h=((max===r?(g-b)/d+(g<b?6:0):max===g?(b-r)/d+2:(r-g)/d+4)*60);
  return ['red','orange','yellow','green','cyan','blue','violet','pink'][Math.floor(((h+22.5)%360)/45)];
}
export function features(t:Candidate):string[] {
  return [...new Set([`category:${t.category}`,`creator:${t.author_id??t.author?.id}`,`style:${t.manifest?.iconStyle??'rounded'}`,
    ...(t.tags??[]).slice(0,10).map(v=>`tag:${v.toLowerCase()}`),
    ...Object.values(t.manifest?.palette??{}).filter((v):v is string=>typeof v==='string').map(v=>`color:${colorBucket(v)}`)])];
}
export function learn(profile:PreferenceProfile, features:string[], weight:number, themeId?:string):PreferenceProfile {
  const next={...profile,weights:{...profile.weights},seen:{...profile.seen}};
  for(const feature of features)next.weights[feature]=Math.min(100,(next.weights[feature]??0)+weight);
  if(themeId)next.seen[themeId]=(next.seen[themeId]??0)+1;
  // Bound stored profiles while retaining the most useful features.
  next.weights=Object.fromEntries(Object.entries(next.weights).sort((a,b)=>b[1]-a[1]).slice(0,500));
  next.seen=Object.fromEntries(Object.entries(next.seen).slice(-1000));
  return next;
}
export function rank<T extends Candidate>(candidates:T[],profile:PreferenceProfile,followed:Set<string>,now=Date.now()):T[] {
  const score=(t:T)=>{
    const affinity=features(t).reduce((sum,f)=>sum+Math.log1p(profile.weights[f]??0),0);
    const age=Math.max(0,(now-new Date(t.created_at??t.createdAt??now).getTime())/86400000);
    return affinity + (followed.has(t.author_id??t.author?.id??'')?3:0) + 2*Math.exp(-age/30) + Math.log1p(t.downloads+(t.likes??0))*.2 - Math.log1p(profile.seen[t.id]??0)*.5;
  };
  const remaining=[...candidates].sort((a,b)=>score(b)-score(a)||a.id.localeCompare(b.id));
  const result:T[]=[];let previous='';
  while(remaining.length){
    // Avoid runs of one creator without discarding personalized ranking.
    const different=remaining.findIndex(t=>(t.author_id??t.author?.id)!==previous);
    const index=result.length%5===4 ? Math.min(remaining.length-1,8) : Math.max(0,different);
    const [item]=remaining.splice(index,1);result.push(item);previous=item.author_id??item.author?.id??'';
  }
  return result;
}
export const discoveryMessages=[
  'Olha o que encontramos para você 👀','Você vai adorar essa seleção','Encontramos alguns temas que combinam com você',
  'Novos temas que podem ser a sua cara','Tem uma seleção esperando por você','Talvez esses temas sejam exatamente o que você procura',
  'Descobrimos novos temas baseados no que você gosta',
];
export function dailyChoice(userId:string,date:string,previousMessage=-1,previousMinute=-1) {
  const seed=createHash('sha256').update(`${userId}:${date}:discovery-v1`).digest();
  let minute=480+seed.readUInt32BE(0)%900;
  if(minute===previousMinute)minute=480+(minute-480+137)%900;
  let template=seed.readUInt32BE(4)%discoveryMessages.length;
  if(template===previousMessage)template=(template+1)%discoveryMessages.length;
  return {minute,template,title:discoveryMessages[template]};
}
