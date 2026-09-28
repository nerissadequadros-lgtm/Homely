import {test} from 'node:test';
import assert from 'node:assert/strict';
import {forecastSummary,searchCities} from '../src/weather.js';
test('weather uses current model data, converts wind and preserves hourly forecast',()=>{
 const now=Date.UTC(2026,8,22,12);const data={properties:{meta:{updated_at:new Date(now-600000).toISOString()},timeseries:Array.from({length:8},(_,i)=>({time:new Date(now+i*3600000).toISOString(),data:{instant:{details:{air_temperature:20+i,relative_humidity:65,wind_speed:2}},next_1_hours:{summary:{symbol_code:'rain'},details:{precipitation_amount:1}}}}))}};
 const result=forecastSummary(data,now);assert.equal(result.temperature,20);assert.equal(result.wind,7.2);assert.equal(result.minimum,20);assert.equal(result.maximum,27);assert.equal(result.hourly.length,6);assert.equal(result.stale,false);
 assert.throws(()=>forecastSummary(data,now+86400000));assert.throws(()=>forecastSummary({}));
});
test('city search ignores accents, prioritizes exact names and hides search metadata',()=>{
 const result=searchCities([{id:'1',name:'São Paulo',country:'BR',latitude:-23.5,longitude:-46.6,timezone:'America/Sao_Paulo',population:10000,search:'sao paulo'},{id:'2',name:'São Paulo de Olivença',country:'BR',latitude:-3,longitude:-69,timezone:'America/Manaus',population:100,search:'sao paulo de olivenca'}],'SÃO PAULO');assert.equal(result.length,2);assert.equal(result[0].name,'São Paulo');assert.equal('search' in result[0],false);
});
