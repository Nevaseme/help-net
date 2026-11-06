const CACHE='pland-lab-v1.6.63-r109-helpnet';
const FILES=['/date-time.mjs','/report-pdf.mjs','/incident-presentation.mjs','/current-location.mjs','/care-policy.mjs','/demo-address-display.mjs','/parcel-addresses.mjs','/parcel-search.mjs','/building-details.mjs','/web1617-impact-collision.svg','/web1617-enclosed.png','/venue-observations.mjs','/community-overview.mjs','/community-screen.mjs','/community-summary.css','/search-controls.mjs','/address-selection.mjs','/address-worker.mjs','/home-registration.mjs','/device-records.mjs','/dom-state.mjs','/profile-home.mjs','/sheet-handle.mjs','/ui-accessibility.mjs','/venue-demo.mjs','/web1617-ui.mjs','/web1617-english.mjs','/menu-english.mjs','/web1617-report-normal.png','/web1617-report-emergency.png','/web1617-safe.svg','/web1617-rescued.svg','/web1617-report-unrated.svg','/','/index.html','/app.mjs','/cloud-client.mjs','/cloud-ui.mjs','/sync.mjs','/local-situation.mjs','/managed-records.mjs','/managed-store.mjs','/operations-export.mjs','/regions.mjs','/feedback.mjs','/incidents.mjs','/places.mjs','/place-ui.mjs','/style.css','/manifest.webmanifest','/icon.svg','/icon-192.png','/icon-512.png','/icon-maskable-512.png','/apple-touch-icon.png','/nav-user.svg','/nav-users.svg','/nav-buildings.svg','/nav-gear.svg','/leaflet.js','/leaflet.css','/tsunami-map.mjs','/tsunami-map-model.mjs','/tsunami-precomputed.mjs','/tsunami-precomputed-atami.json','/nav-map.svg','/nav-list.svg','/menu.svg','/close.svg','/chevron.svg','/home.svg','/crosshair.svg','/form-drafts.mjs','/app-update.mjs','/municipalities.mjs','/city-addresses.mjs','/city-offline.mjs','/city-map-resources.mjs','/vector-basemap.mjs','/maplibre-gl.mjs','/maplibre-gl-shared.mjs','/maplibre-gl-worker.mjs','/maplibre-gl.css','/leaflet-maplibre-gl.js','/building-picker.mjs','/building-risk.mjs'];


self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(FILES)).then(()=>self.skipWaiting())));
self.addEventListener('message',event=>{if(event.data?.type==='ACTIVATE_UPDATE')event.waitUntil(self.skipWaiting());if(event.data?.type==='GET_VERSION')event.ports[0]?.postMessage(CACHE);});
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('pland-lab-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{const url=new URL(event.request.url);if(event.request.method!=='GET'||url.origin!==self.location.origin||!FILES.includes(url.pathname))return;
 event.respondWith(caches.open(CACHE).then(async cache=>(await cache.match(url.pathname))||fetch(event.request)));
});



self.addEventListener('fetch',event=>{const url=new URL(event.request.url);if(event.request.method!=='GET')return;
 const cityData=url.href.startsWith('https://nevaseme.github.io/help-net/data/cities/');
 if(['reload','no-store'].includes(event.request.cache)||!cityData)return;
 event.respondWith((async()=>{for(const name of (await caches.keys()).reverse()){if(!name.startsWith('tasukeai-city-v2-')&&!name.startsWith('tasukeai-city-v1-'))continue;const cache=await caches.open(name);if(!await cache.match('/__city_complete__'))continue;const hit=await cache.match(event.request);if(hit)return hit;}return fetch(event.request);})());
});


