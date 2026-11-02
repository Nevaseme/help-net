// Build-time representation only: original published packages stay unchanged.
import {readFileSync,writeFileSync} from 'node:fs';
import {gzipSync,gunzipSync} from 'node:zlib';
import {createHash} from 'node:crypto';
import {normalizeAddress,CITY_CODES} from '../../lab/web/city-addresses.mjs';
const root=new URL('../../android/app/src/main/assets/cities/',import.meta.url),manifest={version:1,files:[]};
for(const code of CITY_CODES)for(const kind of ['addresses','places']){
 const source=readFileSync(new URL(`${code}-${kind}.json.gzip`,root)),rows=JSON.parse(gunzipSync(source)).rows,parts=[];
 const int=v=>{const b=Buffer.alloc(4);b.writeInt32BE(v);parts.push(b);};
 const number=v=>{const b=Buffer.alloc(8);b.writeDoubleBE(v);parts.push(b);};
 const string=v=>{const b=Buffer.from(v??'','utf8');int(b.length);parts.push(b);};
 int(0x50445331);int(rows.length);
 for(const [id,address,lon,lat,precision,meta={}] of rows){string(id);string(address);number(lon);number(lat);for(const v of [precision,meta.matchLevel??precision,meta.source,meta.sourceVersion,normalizeAddress(address)])string(v);}
 const bytes=gzipSync(Buffer.concat(parts),{level:9}),file=`${code}-${kind}.prepared.gzip`;writeFileSync(new URL(file,root),bytes);
 manifest.files.push({file,rows:rows.length,sourceSha256:createHash('sha256').update(source).digest('hex'),sha256:createHash('sha256').update(bytes).digest('hex')});
 console.log(`${file}: ${rows.length} rows, ${bytes.length} bytes`);
}
writeFileSync(new URL('prepared-manifest.json',root),JSON.stringify(manifest,null,2)+'\n');
