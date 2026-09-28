import type { FastifyInstance } from 'fastify';
import { readFile } from 'node:fs/promises';
import { z } from 'zod';

type City={id:string;name:string;country:string;latitude:number;longitude:number;timezone:string;population:number;search:string};
let cities:Promise<City[]>|undefined;
const fold=(s:string)=>s.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
export function searchCities(list:City[],q:string){const key=fold(q).split(',')[0].trim();return list.filter(c=>c.search.includes(key)).sort((a,b)=>Number(fold(b.name)===key)-Number(fold(a.name)===key)||b.population-a.population).slice(0,12).map(({search,population,...city})=>city);}
export function forecastSummary(raw:any,now=Date.now()) {
 const series=raw?.properties?.timeseries;
 if(!Array.isArray(series)||!series.length)throw new Error('Previsão vazia');
 const future=series.filter((p:any)=>Date.parse(p.time)>=now-3600000).slice(0,25);
 if(!future.length)throw new Error('Previsão expirada');
 const points=future.map((p:any)=>{
  const d=p.data?.instant?.details;
  if(!Number.isFinite(d?.air_temperature))throw new Error('Temperatura inválida');
  return{time:Date.parse(p.time),temperature:d.air_temperature,humidity:d.relative_humidity??null,wind:(d.wind_speed??0)*3.6,symbol:p.data.next_1_hours?.summary?.symbol_code??p.data.next_6_hours?.summary?.symbol_code??'cloudy',rain:p.data.next_1_hours?.details?.precipitation_amount??0};
 });
 return{...points[0],minimum:Math.min(...points.map(p=>p.temperature)),maximum:Math.max(...points.map(p=>p.temperature)),hourly:points.slice(1,7),updatedAt:Date.parse(raw.properties.meta.updated_at),fetchedAt:now,stale:false,provider:'MET Norway',attribution:'MET Norway · CC BY 4.0',sourceUrl:'https://api.met.no/'};
}
type Entry={value:ReturnType<typeof forecastSummary>;expires:number;lastModified?:string};
const cache=new Map<string,Entry>();const pending=new Map<string,Promise<Entry>>();let nextFetch=0;
async function forecast(lat:number,lon:number):Promise<Entry>{
 const key=lat.toFixed(3)+','+lon.toFixed(3);const old=cache.get(key);if(old&&old.expires>Date.now())return old;
 const active=pending.get(key);if(active)return active;
 const work=(async()=>{
  const wait=Math.max(0,nextFetch-Date.now());nextFetch=Date.now()+wait+150;
  if(wait>10000)throw Object.assign(new Error('Tente novamente em instantes.'),{statusCode:429});
  if(wait)await new Promise(r=>setTimeout(r,wait));
  try{
   const headers:Record<string,string>={'User-Agent':process.env.WEATHER_USER_AGENT||'Homely/0.6 (+https://82.29.61.16:8443/health)','Accept':'application/json'};
   if(old?.lastModified)headers['If-Modified-Since']=old.lastModified;
   const response=await fetch(`https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${lat.toFixed(3)}&lon=${lon.toFixed(3)}`,{headers,signal:AbortSignal.timeout(15000)});
   const expires=Math.max(Date.now()+1800000,Date.parse(response.headers.get('expires')||'')||0);
   if(response.status===304&&old){old.expires=expires;return old;}
   if(!response.ok)throw new Error('Provedor indisponível');
   const entry:Entry={value:forecastSummary(await response.json()),expires,lastModified:response.headers.get('last-modified')||undefined};
   if(cache.size>=2000)cache.delete(cache.keys().next().value!);cache.set(key,entry);return entry;
  }catch(e){if(old&&Date.now()-old.value.fetchedAt<21600000){const fallback={...old,expires:Date.now()+300000,value:{...old.value,stale:true}};cache.set(key,fallback);return fallback;}throw Object.assign(new Error('Previsão indisponível. Tente novamente em instantes.'),{statusCode:503});}
 })();pending.set(key,work);try{return await work;}finally{pending.delete(key);}
}
export function registerWeather(app:FastifyInstance){
 app.get('/v1/weather/cities',{config:{rateLimit:{max:30,timeWindow:'1 minute'}}},async req=>{
  const q=z.string().trim().min(2).max(80).parse((req.query as any).q);
  cities??=readFile(new URL('../data/cities.json',import.meta.url),'utf8').then(v=>JSON.parse(v));
  return{items:searchCities(await cities,q),attribution:'GeoNames · CC BY 4.0'};
 });
 app.get('/v1/weather',{config:{rateLimit:{max:60,timeWindow:'1 minute'}}},async(req,reply)=>{
  const q=z.object({lat:z.coerce.number().min(-90).max(90),lon:z.coerce.number().min(-180).max(180)}).strict().parse(req.query);
  const result=await forecast(q.lat,q.lon);reply.header('Cache-Control','private,max-age=300');return result.value;
 });
}
