const versions=[
 {id:'helpnet-neighborhood-placement-pwa',title:'Help Net · PWA 1.6.51 実装確認',summary:'町内会の吹き出しを、旧Webと同じく地域の地点に下端を固定する配置へ戻しました。空き場所探索・引出線・遅れて動く補間を撤去し、見た目は維持しています。',scenes:[['pwa-map','地図','町内会の地点に固定した吹き出し。'],['pwa-details','町内会の詳細','吹き出しを押して開いた町内会の集計。']]},
];
for(const version of versions){
 const section=document.createElement('section');section.className='view';section.id=version.id;
 section.innerHTML=`<header class="comparison-caption" style="height:auto;min-height:126px"><h1 class="comparison-label"></h1><p class="comparison-summary"></p></header><nav aria-label="実装確認画像" style="display:flex;flex-wrap:wrap;gap:8px;margin:12px 0"></nav><img style="display:block;width:100%;height:auto;border-radius:16px"><p style="font-size:14px"><a href="https://tasukeai-sync.tomodachi-takumi.workers.dev/" target="_blank" rel="noopener">Webアプリを開く</a></p>`;
 section.querySelector('h1').textContent=version.title;section.querySelector('.comparison-summary').textContent=version.summary;
 for(const [file,label,alt] of version.scenes){const b=document.createElement('button');b.type='button';b.textContent=label;b.style.cssText='min-height:48px;padding:8px 12px;border:1px solid #617786;border-radius:8px;cursor:pointer;font:inherit';b.onclick=()=>{for(const peer of section.querySelectorAll('nav button')){const active=peer===b;peer.setAttribute('aria-pressed',String(active));peer.style.background=active?'#155eef':'#fff';peer.style.color=active?'#fff':'#183447';}const img=section.querySelector('img');img.src=`helpnet-neighborhood-placement/${file}.png`;img.alt=alt;};section.querySelector('nav').append(b);}
 document.getElementById('board').append(section);section.querySelector('nav button').click();
 if(location.hash===`#${version.id}`)section.scrollIntoView({inline:'start',block:'start'});
}
const android=document.createElement('section');android.className='view';android.id='helpnet-neighborhood-placement-android';
android.innerHTML='<header class="comparison-caption" style="height:auto;min-height:126px"><h1 class="comparison-label">Help Net · Android 1.5.77 実装版</h1><p class="comparison-summary">配置探索を撤去した旧版です。後の実機確認で下端固定が未反映と判明し、右の1.5.78で修正しました。</p></header><p style="padding:20px;font-size:16px;line-height:1.7">ビルドと単体試験298件は成功していましたが、吹き出しの中央が地域の地点に重なっていました。この列は比較履歴として残しています。</p>';
document.getElementById('board').append(android);
if(location.hash===`#${android.id}`)android.scrollIntoView({inline:'start',block:'start'});
const verified=document.createElement('section');verified.className='view';verified.id='helpnet-neighborhood-placement-android-1578';
verified.innerHTML='<header class="comparison-caption" style="height:auto;min-height:126px"><h1 class="comparison-label">Help Net · Android 1.5.78 実装確認</h1><p class="comparison-summary">吹き出しの下端中央を町内会の地点に固定しました。実機で16回の移動・拡大縮小とタップを確認し、見た目は維持しています。</p></header><img src="helpnet-neighborhood-placement/android-map-1578.png" alt="Android実機の配置確認用画面。町内会の地点に下端を固定した2つの吹き出し。" style="display:block;width:100%;height:auto;border-radius:16px"><p style="font-size:14px;line-height:1.7">配置試験用の地図画面です。通常画面でも町内会集計の開閉、地図操作、一覧との往復を確認しました。</p>';
document.getElementById('board').append(verified);
if(location.hash===`#${verified.id}`)verified.scrollIntoView({inline:'start',block:'start'});
