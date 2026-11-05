const versions=[
 {id:'helpnet-condition-fix-pwa',title:'Help Net · PWA 1.6.47 実装確認',summary:'参加案内と保存時の英語表示、320px幅の集計表を修正しました。この旧版の通報自動回避・引出線は指示外の変更のため、右側の最新版で撤去しました。',scenes:[['04-map','地図','旧版の通報自動回避。最新版では撤去。'],['02-people-320','320px幅','英単語を途中で切らず、横にスクロールできる集計表。'],['01-group-english','参加案内','参加案内と入力エラーの英語表示。'],['05-map-details','登録建物','青緑の輪郭を持つ登録建物。'],['pwa-pdf-page','保存したPDF','実際に保存したPDFの1ページ目。']]},
 {id:'helpnet-condition-fix-android',title:'Help Net · Android 1.5.74 実装確認',summary:'人数・通知・エラーの英語表示を補完した旧版です。通報の自動回避は指示外のため最新版で撤去しました。Android実装をエミュレーターで操作し、PDFの保存完了も確認しています。',scenes:[['android-map','地図','旧版の通報自動回避と引出線。最新版では撤去。'],['android-pdf-saved','PDF保存','Androidの保存先選択後に表示された保存完了。']]}
];
for(const version of versions){
 const section=document.createElement('section');section.className='view';section.id=version.id;
 section.innerHTML=`<header class="comparison-caption" style="height:auto;min-height:126px"><h1 class="comparison-label"></h1><p class="comparison-summary"></p></header><nav aria-label="実装確認画像" style="display:flex;flex-wrap:wrap;gap:8px;margin:12px 0"></nav><img style="display:block;width:100%;height:auto;border-radius:16px"><p style="font-size:14px"><a href="https://tasukeai-sync.tomodachi-takumi.workers.dev/" target="_blank" rel="noopener">Webアプリを開く</a></p>`;
 section.querySelector('h1').textContent=version.title;section.querySelector('.comparison-summary').textContent=version.summary;
 for(const [file,label,alt] of version.scenes){const b=document.createElement('button');b.type='button';b.textContent=label;b.style.cssText='min-height:48px;padding:8px 12px;border:1px solid #617786;border-radius:8px;cursor:pointer;font:inherit';b.onclick=()=>{for(const peer of section.querySelectorAll('nav button')){const active=peer===b;peer.setAttribute('aria-pressed',String(active));peer.style.background=active?'#155eef':'#fff';peer.style.color=active?'#fff':'#183447';}const img=section.querySelector('img');img.src=`helpnet-condition-fix/${file}.png`;img.alt=alt;};section.querySelector('nav').append(b);}
 document.getElementById('board').append(section);section.querySelector('nav button').click();
 if(location.hash===`#${version.id}`)section.scrollIntoView({inline:'start',block:'start'});
}
