import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store} from '../web/sync.mjs';
import {CloudClient} from '../web/cloud-client.mjs';
import {venuePositionFix,venuePositions,isAssignedVenuePosition} from '../web/venue-demo.mjs';

const device='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',group='venue-location-test';
const venue={groupId:group,sessionId:'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',startedAt:1000,positionMode:'KYOTO',placements:[{deviceId:device,latitude:35.0145,longitude:135.7625}]};
const gps={timestamp:2345,coords:{latitude:35.6665,longitude:139.7495,accuracy:14}};

test('Kyoto retains assigned coordinates but uses only successful acquisition timestamps',()=>{
 const initial=venuePositions(venue)[0];assert.equal(initial.acquiredAt,null);assert.equal(initial.latitude,venue.placements[0].latitude);
 const fix=venuePositionFix(venue,device,gps);
 assert.equal(fix.latitude,venue.placements[0].latitude);assert.equal(fix.longitude,venue.placements[0].longitude);assert.equal(fix.accuracyMeters,0);assert.equal(fix.acquiredAt,gps.timestamp);
 assert.ok(isAssignedVenuePosition(venue,fix));assert.equal(venuePositions(venue,[fix])[0].acquiredAt,gps.timestamp);
 assert.equal(venuePositionFix(venue,device,null,fix),null);
 assert.equal(venuePositionFix(venue,device,{...gps,timestamp:999},fix),null);
 assert.equal(venuePositionFix(venue,device,{...gps,timestamp:2345},fix),null);
 assert.equal(venuePositionFix(venue,device,{...gps,coords:{...gps.coords,latitude:NaN}},fix),null);
 assert.equal(venuePositions(venue,[fix])[0].acquiredAt,gps.timestamp);
 const next=venuePositionFix(venue,device,{...gps,timestamp:3456},fix);assert.equal(next.revision,2);assert.equal(next.acquiredAt,3456);
});

test('Kyoto ignores real GPS and earlier sessions, while ACTUAL keeps measured coordinates',()=>{
 const actual={...venue,positionMode:'ACTUAL'},fix=venuePositionFix(actual,device,gps);
 assert.equal(fix.latitude,gps.coords.latitude);assert.equal(fix.longitude,gps.coords.longitude);assert.equal(fix.accuracyMeters,gps.coords.accuracy);
 assert.deepEqual(venuePositions(actual,[fix]),[fix]);assert.equal(venuePositions(venue,[fix])[0].acquiredAt,null);
 assert.equal(venuePositions(venue,[{...venuePositionFix(venue,device,gps),acquiredAt:999}])[0].acquiredAt,null);
});

test('Kyoto shares only assigned coordinates, retries failed saves, and a receiver shows the real timestamp',async()=>{
 const store=await Store.open(crypto.randomUUID(),indexedDB),receiver=await Store.open(crypto.randomUUID(),indexedDB);
 const json=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'content-type':'application/json'}}),uploaded=[];
 let fail=true;
 const client=new CloudClient(store,device,{fetcher:async(url,options)=>{
  const row=JSON.parse(options.body).records[0];uploaded.push(row);
  return fail?json({error:{code:'storage_unavailable',message:'retry'}},503):json({acks:[{collection:'positions',key:device,status:'stored'}]});
 }});
 client.requireSession=async()=>({groupId:group});client.roster=async()=>{};client.pull=async()=>0;
 try{
  await store.update('meta','venue:'+group,()=>venue);
  await store.savePosition(venuePositionFix({...venue,positionMode:'ACTUAL'},device,gps));
  assert.equal((await client.sync(group)).sent,0);assert.equal(uploaded.length,0);
  const assigned=venuePositionFix(venue,device,{...gps,timestamp:3456},(await store.positions(group))[0]);await store.savePosition(assigned);
  assert.equal((await client.sync(group)).pending,1);fail=false;assert.equal((await client.sync(group)).sent,1);assert.equal((await client.sync(group)).sent,0);
  assert.equal(uploaded.length,2);assert.deepEqual(uploaded[1],{collection:'positions',record:assigned});
  const recipient=new CloudClient(receiver,'cccccccc-cccc-4ccc-8ccc-cccccccccccc');await recipient.merge(uploaded[1].collection,uploaded[1].record);
  const shown=venuePositions(venue,await receiver.positions(group))[0];assert.equal(shown.acquiredAt,3456);assert.equal(shown.longitude,135.7625);
 }finally{store.close();receiver.close();}
});
