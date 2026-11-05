const versions=[
 {id:'helpnet-venue-pwa',title:'Help Net · PWA 1.6.52 実装確認',summary:'英語を初期表示にし、安否登録後に京都の割当位置の案内を表示します。次回開催から町内会を約半分に縮小し、会場参加者は全員中央に所属します。',scenes:[['pwa-earthquake-safety','安否登録前','英語の安否登録画面。登録まで他画面への移動を制限。'],['pwa-earthquake-notice','京都の案内','承認済みの英語案内。'],['pwa-ja-notice','日本語の案内','承認済みの日本語案内。'],['pwa-earthquake-map','Show safe オン','英語地名と京都の現在地の青丸。'],['pwa-earthquake-hide-safe','Show safe オフ','無事の人を除外した地図。']]},
 {id:'helpnet-venue-android',title:'Help Net · Android 1.5.79 実機確認',summary:'地震とDrillの両モードを同じ訓練動作に揃え、英語表示・案内・安否登録の制限を実機で確認しました。新配置で連続移動・拡大縮小を行い、停止・クラッシュなし。',scenes:[['android-earthquake-safety','安否登録前','Android実機の安否登録画面。'],['android-earthquake-notice','京都の案内','Android実機の承認済み英語案内。'],['android-earthquake-map','地震モード','Android実機の新しい町内会配置。'],['android-drill-map','Drillモード','Android実機のDrill表示。']]}
];
for(const version of versions){
 const section=document.createElement('section');section.className='view';section.id=version.id;
 section.innerHTML='<header class="comparison-caption" style="height:auto;min-height:126px"><h1 class="comparison-label"></h1><p class="comparison-summary"></p></header><nav aria-label="実装確認画像" style="display:flex;flex-wrap:wrap;gap:8px;margin:12px 0"></nav><img style="display:block;width:100%;height:auto;border-radius:16px"><p style="font-size:14px"><a href="https://tasukeai-sync.tomodachi-takumi.workers.dev/" target="_blank" rel="noopener">Webアプリを開く</a></p>';
 section.querySelector('h1').textContent=version.title;section.querySelector('.comparison-summary').textContent=version.summary;
 for(const [file,label,alt] of version.scenes){const b=document.createElement('button');b.type='button';b.textContent=label;b.style.cssText='min-height:48px;padding:8px 12px;border:1px solid #617786;border-radius:8px;cursor:pointer;font:inherit';b.onclick=()=>{for(const peer of section.querySelectorAll('nav button')){const active=peer===b;peer.setAttribute('aria-pressed',String(active));peer.style.background=active?'#155eef':'#fff';peer.style.color=active?'#fff':'#183447';}const img=section.querySelector('img');img.src=`helpnet-venue-experience/${file}.png`;img.alt=alt;};section.querySelector('nav').append(b);}
 document.getElementById('board').append(section);section.querySelector('nav button').click();
 if(location.hash===`#${version.id}`)section.scrollIntoView({inline:'start',block:'start'});
}
