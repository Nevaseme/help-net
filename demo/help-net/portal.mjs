const ja={iosBrowser:'Safariで開き、Help Netをホーム画面に追加できます。',install:'インストール手順',title:'Help Netに参加',group:'グループ名',password:'参加パスワード',join:'Help Netを開き、上の情報を入力してグループに参加してください。',open:'Help Netを開く',homescreen:'ホーム画面に追加',ios1:'上のリンクをSafariで開きます。',ios2:'共有ボタンを押します。Safariの表示によっては、先に「その他」を開きます。',ios3:'「ホーム画面に追加」を選びます。「Webアプリとして開く」が表示されたらオンにし、「追加」を押します。',ios4:'ホーム画面からHelp Netを開き、グループに参加します。',apple:'Appleの操作案内',browser:'ブラウザで使う',noapk:'APKをダウンロードせずにChromeで利用できます。',android1:'ChromeでHelp Netを開きます。',android2:'⋮メニューから「ホーム画面に追加」を選び、「インストール」が表示されたら押します。',android3:'Help Netを開き、グループに参加します。',google:'Googleの操作案内',native:'Androidアプリ',download:'APKをダウンロード',apk1:'APKをダウンロードし、保存されたファイルを開きます。',apk2:'Androidで確認が表示された場合は、使用したブラウザからのインストールを許可し、画面の案内に従います。',apk3:'Help Netを開き、上のグループ名と参加パスワードを入力します。',difference:'インターネットなしでの近距離共有はAndroidアプリで利用できます。ブラウザ版で他の人と情報を共有するには、インターネット接続が必要です。'};
const nodes=[...document.querySelectorAll('[data-i18n]')],en=Object.fromEntries(nodes.map(n=>[n.dataset.i18n,n.textContent]));
function language(lang){
 document.documentElement.lang=lang;document.title=lang==='ja'?'Help Netに参加':'Join Help Net';
 for(const n of nodes)n.textContent=(lang==='ja'?ja:en)[n.dataset.i18n];
 for(const b of document.querySelectorAll('[data-language]'))b.setAttribute('aria-pressed',String(b.dataset.language===lang));
 document.querySelector('.languages').setAttribute('aria-label',lang==='ja'?'表示言語':'Language');
 document.querySelector('[data-i18n="google"]').href='https://support.google.com/chrome/answer/9658361?co=GENIE.Platform%3DAndroid&hl='+lang;
 try{localStorage.setItem('help-net-guide-language',lang);}catch{}
}
for(const b of document.querySelectorAll('[data-language]'))b.addEventListener('click',()=>language(b.dataset.language));
let initial='en';try{if(localStorage.getItem('help-net-guide-language')==='ja')initial='ja';}catch{}language(initial);
