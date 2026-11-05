const versions=[
 {id:'helpnet-report-location-pwa',title:'Help Net · PWA 1.6.50 実装確認',summary:'通報アイコンを登録地点へ戻し、Blocked roadの初期地点だけを道路上に訂正しました。建物詳細の表示範囲とPDF保存を維持し、Safetyの保存結果も言語切替とダーク表示に対応します。',scenes:[['pwa-map','地図','道路上のBlocked road。全通報の自動回避・引出線はありません。'],['pwa-building','建物詳細','地図を残した建物詳細と、所属町内会の範囲外という短い説明。'],['pwa-safety','Safety','英語へ切り替えた後の保存結果もSavedと表示。']]},
 {id:'helpnet-report-location-android',title:'Help Net · Android 1.5.76 実装確認',summary:'通報の自動回避を撤去し、登録地点との一致・輪郭の描画順・開閉後のMap維持をエミュレーターで確認しました。凡例の外側を押して閉じる操作も修正しています。',scenes:[['android-map','地図','訂正後の道路地点に置いた通報。登録地点からの一律移動はありません。'],['android-closed','通報を閉じた後','通報を閉じてもMapを維持。']]}
];
for(const version of versions){
 const section=document.createElement('section');section.className='view';section.id=version.id;
 section.innerHTML=`<header class="comparison-caption" style="height:auto;min-height:126px"><h1 class="comparison-label"></h1><p class="comparison-summary"></p></header><nav aria-label="実装確認画像" style="display:flex;flex-wrap:wrap;gap:8px;margin:12px 0"></nav><img style="display:block;width:100%;height:auto;border-radius:16px"><p style="font-size:14px"><a href="https://tasukeai-sync.tomodachi-takumi.workers.dev/" target="_blank" rel="noopener">Webアプリを開く</a></p>`;
 section.querySelector('h1').textContent=version.title;section.querySelector('.comparison-summary').textContent=version.summary;
 for(const [file,label,alt] of version.scenes){const b=document.createElement('button');b.type='button';b.textContent=label;b.style.cssText='min-height:48px;padding:8px 12px;border:1px solid #617786;border-radius:8px;cursor:pointer;font:inherit';b.onclick=()=>{for(const peer of section.querySelectorAll('nav button')){const active=peer===b;peer.setAttribute('aria-pressed',String(active));peer.style.background=active?'#155eef':'#fff';peer.style.color=active?'#fff':'#183447';}const img=section.querySelector('img');img.src=`helpnet-report-location/${file}.png`;img.alt=alt;};section.querySelector('nav').append(b);}
 document.getElementById('board').append(section);section.querySelector('nav button').click();
 if(location.hash===`#${version.id}`)section.scrollIntoView({inline:'start',block:'start'});
}
