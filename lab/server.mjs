import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {WebSocketServer} from 'ws';
import {Router} from './router.mjs';
const root=path.dirname(fileURLToPath(import.meta.url));
const config=JSON.parse(fs.readFileSync(path.join(root,'.local/config.json'),'utf8'));
const router=new Router(config.token);
const allowed=new Map(['search-controls.mjs','address-worker.mjs','address-selection.mjs','home-registration.mjs','device-records.mjs','dom-state.mjs','profile-home.mjs','sheet-handle.mjs','ui-accessibility.mjs','venue-demo.mjs','web1617-ui.mjs','web1617-english.mjs','web1617-report-normal.png','web1617-report-emergency.png','web1617-safe.svg','web1617-rescued.svg','web1617-report-unrated.svg','index.html','app.mjs','cloud-client.mjs','cloud-ui.mjs','sync.mjs','local-situation.mjs','managed-records.mjs','managed-store.mjs','operations-export.mjs','regions.mjs','feedback.mjs','incidents.mjs','places.mjs','place-ui.mjs','style.css','manifest.webmanifest','icon.svg','nav-user.svg','nav-users.svg','nav-buildings.svg','nav-gear.svg','leaflet.js','leaflet.css','tsunami-map.mjs','tsunami-map-model.mjs','tsunami-precomputed.mjs','tsunami-precomputed-atami.json','nav-map.svg','nav-list.svg','menu.svg','close.svg','chevron.svg','home.svg','crosshair.svg','form-drafts.mjs','app-update.mjs','municipalities.mjs','city-addresses.mjs','city-offline.mjs','city-map-resources.mjs','vector-basemap.mjs','maplibre-gl.mjs','maplibre-gl-shared.mjs','maplibre-gl-worker.mjs','maplibre-gl.css','leaflet-maplibre-gl.js','building-picker.mjs','building-risk.mjs','sw.js'].map(f=>['/'+f,f]));

const mime={'.html':'text/html; charset=utf-8','.mjs':'text/javascript; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.webmanifest':'application/manifest+json','.svg':'image/svg+xml','.json':'application/json','.png':'image/png'};
const server=https.createServer({key:fs.readFileSync(path.join(root,'.local/server.key')),cert:fs.readFileSync(path.join(root,'.local/server.pem'))},async(req,res)=>{
 const url=new URL(req.url,'https://localhost');const file=allowed.get(url.pathname==='/'?'/index.html':url.pathname);
 if(req.method!=='GET'||!file){res.writeHead(404);res.end();return;}
 res.writeHead(200,{'Content-Type':mime[path.extname(file)],'Cache-Control':'no-cache','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Content-Security-Policy':"default-src 'self'; connect-src 'self' https://nevaseme.github.io https://tiles.openfreemap.org; worker-src 'self' blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' blob: data: https://cyberjapandata.gsi.go.jp; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"});
 fs.createReadStream(path.join(root,'web',file)).pipe(res);
});
const wss=new WebSocketServer({server,path:'/socket',maxPayload:32000});
wss.on('connection',(ws,req)=>{
 const origin=req.headers.origin;
 // Native Android has no Origin; web clients must have the same origin as this server.
 if(origin&&origin!==`https://${req.headers.host}`){ws.close(1008,'origin rejected');return;}
 const timer=setTimeout(()=>{if(!router.clients.has(ws))ws.close(1008,'join timeout');},5000);
 ws.isAlive=true;ws.on('pong',()=>{ws.isAlive=true;});
 ws.on('message',(data,binary)=>{if(binary)ws.close(1003,'text only');else router.message(ws,data.toString());});
 ws.on('close',()=>{clearTimeout(timer);router.leave(ws);});ws.on('error',()=>{});
});
const heartbeat=setInterval(()=>{for(const ws of wss.clients){if(!ws.isAlive){ws.terminate();continue;}ws.isAlive=false;ws.ping();}},15000);
server.on('close',()=>clearInterval(heartbeat));
server.listen(config.port,'0.0.0.0',()=>console.log(`PlanD lab HTTPS :${config.port}; no record storage; phase AB. See .local/connection.txt for device setup.`));
