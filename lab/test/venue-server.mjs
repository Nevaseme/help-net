import http from 'node:http';
import {DatabaseSync} from 'node:sqlite';
import {readFile} from 'node:fs/promises';
import {extname} from 'node:path';
import {Journal} from '../../cloud-sync/src/journal.mjs';
import {handle} from '../../cloud-sync/src/http.mjs';
export async function startVenueServer({cityFixture=false}={}){
 const groups=new Map();
 const env={SIGNUP_LIMITER:{limit:async()=>({success:true})},GROUPS:{getByName(name){
  if(!groups.has(name)){
   const db=new DatabaseSync(':memory:');const journal=new Journal({all:(q,...p)=>db.prepare(q).all(...p),run:(q,...p)=>db.prepare(q).run(...p),transaction:fn=>{db.exec('BEGIN');try{const r=fn();db.exec('COMMIT');return r;}catch(e){db.exec('ROLLBACK');throw e;}},flush:async()=>{}});
   groups.set(name,{db,journal,async dispatch(op,token,args){try{
    const call=async(group,method,input,auth)=>{const r=await env.GROUPS.getByName(group).dispatch(method,auth,input);if(r.status!==200)throw Object.assign(Error(r.body.error.message),{status:r.status,code:r.body.error.code});return r.body;};
    const body=await(op.startsWith('directory')?journal[op](args,call):op==='create'||op==='join'?journal[op](args):op==='pull'?journal.pull(token,args.after,args.limit):journal[op](token,args));return {status:200,body};
   }catch(e){return {status:e.status??503,body:{error:{code:e.code??'storage_unavailable',message:e.message}}};}}});
  }return groups.get(name);
 }}};
 const mime={'.html':'text/html','.mjs':'text/javascript','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.webmanifest':'application/manifest+json'};
 const server=http.createServer(async(req,res)=>{try{
  const url=new URL(req.url,'http://'+req.headers.host);
  if(url.pathname.startsWith('/api/')){const chunks=[];for await(const c of req)chunks.push(c);const response=await handle(new Request(url,{method:req.method,headers:req.headers,...(req.method==='POST'?{body:Buffer.concat(chunks)}:{})}),env);res.writeHead(response.status,Object.fromEntries(response.headers));res.end(Buffer.from(await response.arrayBuffer()));return;}
  if(cityFixture&&url.pathname.startsWith('/data/cities/')){const relative=decodeURIComponent(url.pathname.slice('/data/cities/'.length));if(relative.split('/').some(p=>p==='..')||relative.includes('\\'))throw Error('path');const bytes=await readFile(new URL('../../demo/data/cities/'+relative,import.meta.url));res.writeHead(200,{'content-type':mime[extname(relative)]||'application/octet-stream'});res.end(bytes);return;}
  const file=url.pathname==='/'?'index.html':url.pathname.slice(1);if(file.includes('/')||file.includes('..'))throw Error('path');
  let body=await readFile(new URL('../web/'+file,import.meta.url));if(cityFixture&&['city-addresses.mjs','sw.js'].includes(file))body=Buffer.from(body.toString().replace('https://nevaseme.github.io/safety_confirm/data/cities/',url.origin+'/data/cities/'));res.writeHead(200,{'content-type':mime[extname(file)]||'application/octet-stream'});res.end(body);
 }catch{res.writeHead(404);res.end();}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));return {base:'http://127.0.0.1:'+server.address().port,close:async()=>{await new Promise(r=>server.close(r));groups.forEach(g=>g.db.close());}};
}
