const versions=[
 {id:'helpnet-four-block-pwa',title:'Help Net · PWA 1.6.57 約4街区版',summary:'画像の赤枠を基準に3町内会をそれぞれ約4街区にし、128人を96棟へ分散しました。小さな建物の割当を除外し、参加者32人の住居は32棟、屋内90人は自宅81人・別棟9人です。',scenes:[['pwa-overview','3町内会','道路で区切った約4街区ずつの西・中央・東。'],['pwa-homes','Show safe オン','32棟へ住居を分散した32参加者とサンプル住民。'],['pwa-focused','Show safe オフ','無事の人を隠して対応が必要な人を表示。'],['pwa-building','住居の建物','住居に関連づいた建物詳細と応答なし一覧。']]}
];
for(const version of versions){
 const section=document.createElement('section');section.className='view';section.id=version.id;
 section.innerHTML='<header class="comparison-caption" style="height:auto;min-height:126px"><h1></h1><p></p></header><nav aria-label="配置修正の確認画像" style="display:flex;flex-wrap:wrap;gap:8px;margin:12px 0"></nav><img style="display:block;width:100%;height:auto;border-radius:16px"><p><a href="https://tasukeai-sync.tomodachi-takumi.workers.dev/" target="_blank" rel="noopener">Webアプリを開く</a></p>';
 section.querySelector('h1').textContent=version.title;section.querySelector('header p').textContent=version.summary;
 for(const [file,label,alt] of version.scenes){const button=document.createElement('button');button.type='button';button.textContent=label;button.style.cssText='min-height:48px;padding:8px 12px;border:1px solid #617786;border-radius:8px;cursor:pointer;font:inherit';button.onclick=()=>{for(const peer of section.querySelectorAll('nav button')){const active=peer===button;peer.setAttribute('aria-pressed',String(active));peer.style.background=active?'#155eef':'#fff';peer.style.color=active?'#fff':'#183447';}const img=section.querySelector('img');img.src=`helpnet-four-block/${file}.png`;img.alt=alt;};section.querySelector('nav').append(button);}
 document.getElementById('board').append(section);section.querySelector('nav button').click();
 if(location.hash===`#${version.id}`)section.scrollIntoView({inline:'start',block:'start'});
}
