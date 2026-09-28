import {test} from 'node:test';
import assert from 'node:assert/strict';
import {dailyChoice,emptyProfile,features,learn,rank,colorBucket} from '../src/recommendation-model.js';

test('daily schedule varies within local window and avoids consecutive message repetition',()=>{
  const minutes=new Set<number>();let message=-1,minute=-1;
  for(let day=1;day<=28;day++) {
    const choice=dailyChoice('user-1',`2026-09-${String(day).padStart(2,'0')}`,message,minute);
    assert.ok(choice.minute>=480 && choice.minute<1380);assert.notEqual(choice.template,message);assert.notEqual(choice.minute,minute);
    assert.deepEqual(choice,dailyChoice('user-1',`2026-09-${String(day).padStart(2,'0')}`,message,minute));
    minutes.add(choice.minute);message=choice.template;minute=choice.minute;
  }
  assert.ok(minutes.size>20);
});
test('strong interest ranks matching theme while preserving a bounded preference profile',()=>{
  const base={tags:[],manifest:{iconStyle:'circle',palette:{accent:'#F898AE'}},downloads:0,createdAt:'2026-09-01'};
  const preferred={...base,id:'a',category:'Anime',author:{id:'alice'}};
  const unrelated={...base,id:'b',category:'Natureza',author:{id:'bob'},manifest:{iconStyle:'square',palette:{accent:'#00AA00'}}};
  let p=emptyProfile();for(let i=0;i<12;i++)p=learn(p,features(preferred),3,'seen');
  assert.equal(rank([unrelated,preferred],p,new Set(),Date.parse('2026-09-24'))[0].id,'a');
  assert.equal(p.seen.seen,12);assert.equal(colorBucket('#FFFFFF'),'light');assert.equal(colorBucket('#000000'),'dark');
  for(let i=0;i<1500;i++)p=learn(p,[`tag:${i}`],1,`${i}`);
  assert.ok(Object.keys(p.weights).length<=500);assert.ok(Object.keys(p.seen).length<=1000);
});
