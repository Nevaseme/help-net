from pathlib import Path
import shutil
source=Path('C:/Users/TAKUMI/.codex/visualizations/2026/10/24/01a16c0c-f86e-7220-ad05-e96afa7746d4/menu-search-1542')
assets=Path('demo/assets/menu-search-1542');assets.mkdir(parents=True,exist_ok=True)
for original,name in [('android/tower.png','android-search.png'),('android/settings.png','android-settings.png'),('android/ward.png','android-address.png'),('pwa/facility-search.png','pwa-search.png'),('pwa/settings.png','pwa-settings.png'),('pwa/address-search.png','pwa-address.png')]:shutil.copyfile(source/original,assets/name)
columns=''
for platform,version,key in [('Android','1.5.42','android'),('PWA','1.6.16','pwa')]:
 columns+=f'''<section class="sharing-view" id="menu-search-{key}"><div class="comparison-caption"><p class="comparison-label">{platform} {version} · メニュー操作と住居検索</p><p class="comparison-summary">設定からメニューボタンで戻り、横棒のタップ・下スワイプでも閉じられます。住所と施設名から候補を探し、地図で建物を確認できます。</p></div><figure class="pwa-screen"><img src="assets/menu-search-1542/{key}-search.png" alt="京都タワーの検索候補"><figcaption>{platform}実装画面・架空グループ</figcaption></figure><details><summary>設定画面</summary><img src="assets/menu-search-1542/{key}-settings.png" alt="タップと下スワイプで閉じられる設定" style="width:100%;height:auto"></details><details><summary>左京区の住所候補</summary><img src="assets/menu-search-1542/{key}-address.png" alt="左京区の住所検索結果" style="width:100%;height:auto"></details></section>'''
for folder in [Path('demo'),Path('.implementation/cloud-demo-publish/demo')]:
 p=folder/'cloud-sharing.html';s=p.read_text(encoding='utf-8')
 if 'id="menu-search-android"' not in s:p.write_text(s.replace('</main>',columns+'</main>'),encoding='utf-8')
shutil.copytree(assets,Path('.implementation/cloud-demo-publish/demo/assets/menu-search-1542'),dirs_exist_ok=True)
p=Path('tools/verify-city-comparison.mjs');s=p.read_text().replace('city-android-1541','menu-search-android').replace('city-pwa-1615','menu-search-pwa').replace('city=1541','menu-search=1542').replace('/city-1541/comparison-','/menu-search-1542/comparison-').replace('.implementation/address-city/comparison-verification.json','.implementation/menu-search/comparison-verification.json');Path('tools/verify-menu-search-comparison.mjs').write_text(s)
