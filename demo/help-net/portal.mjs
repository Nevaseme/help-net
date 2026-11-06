const ja={desktopBrowser:'ブラウザでHelp Netを利用できます。',iosBrowser:'Safariで開き、Help Netをホーム画面に追加できます。',install:'インストール手順',title:'Help Netに参加',group:'グループ名',password:'参加パスワード',join:'Help Netを開き、上の情報を入力してグループに参加してください。',open:'Help Netを開く',homescreen:'ホーム画面に追加',ios1:'上のリンクをSafariで開きます。',ios2:'共有ボタンを押します。Safariの表示によっては、先に「その他」を開きます。',ios3:'「ホーム画面に追加」を選びます。「Webアプリとして開く」が表示されたらオンにし、「追加」を押します。',ios4:'ホーム画面からHelp Netを開き、グループに参加します。',apple:'Appleの操作案内',browser:'ブラウザで使う',noapk:'APKをダウンロードせずにChromeで利用できます。',android1:'ChromeでHelp Netを開きます。',android2:'⋮メニューから「ホーム画面に追加」を選び、「インストール」が表示されたら押します。',android3:'Help Netを開き、グループに参加します。',google:'Googleの操作案内',native:'Androidアプリ',download:'APKをダウンロード',apk1:'APKをダウンロードし、保存されたファイルを開きます。',apk2:'Androidで確認が表示された場合は、使用したブラウザからのインストールを許可し、画面の案内に従います。',apk3:'Help Netを開き、上のグループ名と参加パスワードを入力します。',difference:'インターネットなしでの近距離共有はAndroidアプリで利用できます。ブラウザ版で他の人と情報を共有するには、インターネット接続が必要です。'};
Object.assign(ja,{iosBrowser:'Help Netを開き、ホーム画面に追加します。',ios1:'上のリンクからHelp Netを開きます。',ios2:'ブラウザの共有メニューを開きます。Safariの表示によっては、先に「その他」を開きます。',iosFallback:'「ホーム画面に追加」が見つからない場合は、下のリンクをコピーし、Safariを開いてアドレス欄に貼り付けてください。',copyLink:'Help Netのリンクをコピー',noapk:'APKをダウンロードせずにブラウザで利用できます。',android1:'上のリンクからHelp Netを開きます。',android2:'ブラウザのメニューで「アプリをインストール」または「ホーム画面に追加」を選びます。項目名はブラウザによって異なります。',androidFallback:'インストール項目が見つからない場合は、下のリンクをコピーし、Chromeを開いてアドレス欄に貼り付けてください。ブラウザのまま利用することもできます。',apkFallback:'別のアプリ内でこのページを開いていてダウンロードが始まらない場合は、そのアプリのメニューからブラウザで開き直してください。',otherDevice:'別の端末をお使いですか？'});
const nodes=[...document.querySelectorAll('[data-i18n]')],en=Object.fromEntries(nodes.map(n=>[n.dataset.i18n,n.textContent]));
function language(lang){
 document.documentElement.lang=lang;document.title=lang==='ja'?'Help Netに参加':'Join Help Net';
 for(const n of nodes)n.textContent=(lang==='ja'?ja:en)[n.dataset.i18n];
 for(const b of document.querySelectorAll('[data-language]'))b.setAttribute('aria-pressed',String(b.dataset.language===lang));
 document.querySelector('.languages').setAttribute('aria-label',lang==='ja'?'表示言語':'Language');
 document.querySelector('[data-i18n="google"]').href='https://support.google.com/chrome/answer/9658361?co=GENIE.Platform%3DAndroid&hl='+lang;
 for(const n of document.querySelectorAll('[data-copy-status],[data-device-status]'))n.textContent='';
 try{localStorage.setItem('help-net-guide-language',lang);}catch{}
}
for(const b of document.querySelectorAll('[data-language]'))b.addEventListener('click',()=>language(b.dataset.language));
let initial='en';try{if(localStorage.getItem('help-net-guide-language')==='ja')initial='ja';}catch{}language(initial);
const choices=[...document.querySelectorAll('[data-device-choice]')];
function selectDevice(device,announce=false){
 document.documentElement.dataset.device=device;
 for(const button of choices)button.setAttribute('aria-pressed',String(button.dataset.deviceChoice===device));
 if(announce){const name=choices.find(b=>b.dataset.deviceChoice===device).textContent;document.querySelector('[data-device-status]').textContent=document.documentElement.lang==='ja'?`${name}向けの案内を表示しました。`:`Showing instructions for ${name}.`;document.querySelector(`#${device}-title`).scrollIntoView({block:'start',behavior:'instant'});}
}
for(const button of choices)button.addEventListener('click',()=>selectDevice(button.dataset.deviceChoice,true));
selectDevice(document.documentElement.dataset.device);
for(const button of document.querySelectorAll('[data-copy-pwa]'))button.addEventListener('click',async()=>{
 const url=document.querySelector('[data-pwa]').href,status=button.parentElement.querySelector('[data-copy-status]');let copied=false;
 try{await navigator.clipboard.writeText(url);copied=true;}catch{
  const input=document.createElement('textarea');input.value=url;input.setAttribute('aria-hidden','true');input.style.cssText='position:fixed;opacity:0;pointer-events:none';document.body.append(input);input.select();
  try{copied=document.execCommand('copy');}catch{}input.remove();button.focus({preventScroll:true});
 }
 status.textContent=document.documentElement.lang==='ja'?(copied?'リンクをコピーしました。':'コピーできませんでした。「Help Netを開く」または「ブラウザで使う」のリンクを長押ししてコピーしてください。'):(copied?'Link copied.':'Could not copy. Press and hold the Open Help Net or Use in your browser link, then choose Copy link.');
});
