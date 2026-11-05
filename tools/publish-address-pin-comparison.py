from pathlib import Path
import shutil
source=Path('C:/Users/TAKUMI/.codex/visualizations/2026/10/24/01a16c0c-f86e-7220-ad05-e96afa7746d4/address-pin-1543');assets=Path('demo/assets/address-pin-1543');assets.mkdir(parents=True,exist_ok=True)
for key,files in [('android',['pin','training','search']),('pwa',['pin','training','risk'])]:
 for name in files:shutil.copyfile(source/key/(name+'.png'),assets/(key+'-'+name+'.png'))
columns=''
for key,label,version,extra in [('android','Android','1.5.43','search'),('pwa','PWA','1.6.17','risk')]:
 columns+=f'''<section class="sharing-view" id="address-pin-{key}"><div class="comparison-caption"><p class="comparison-label">{label} {version} · 住所検索と訓練の位置</p><p class="comparison-summary">住所を検索して地図を動かし、ピンの位置で住居を登録できます。訓練開始時には管理者を含む参加者へ京都の架空位置を割り当てるか選べます。</p></div><figure class="pwa-screen"><img src="assets/address-pin-1543/{key}-pin.png" alt="住所検索と中央のピンで住居を選ぶ"><figcaption>{label}実装画面・架空グループ</figcaption></figure><details><summary>訓練の位置設定</summary><img src="assets/address-pin-1543/{key}-training.png" alt="京都に架空の位置を割り当てる選択欄" style="width:100%;height:auto"></details><details><summary>{'検索候補' if key=='android' else '京都の建物評価'}</summary><img src="assets/address-pin-1543/{key}-{extra}.png" alt="{'施設名の検索候補' if key=='android' else '同じ地図上の京都建物評価'}" style="width:100%;height:auto"></details></section>'''
for folder in [Path('demo'),Path('.implementation/cloud-demo-publish/demo')]:
 p=folder/'cloud-sharing.html';s=p.read_text(encoding='utf-8')
 if 'id="address-pin-android"' not in s:p.write_text(s.replace('</main>',columns+'</main>'),encoding='utf-8')
shutil.copytree(assets,Path('.implementation/cloud-demo-publish/demo/assets/address-pin-1543'),dirs_exist_ok=True)
s=Path('tools/verify-menu-search-comparison.mjs').read_text().replace('menu-search-android','address-pin-android').replace('menu-search-pwa','address-pin-pwa').replace('menu-search=1542','address-pin=1543').replace('menu-search-1542','address-pin-1543').replace('.implementation/menu-search/','.implementation/address-pin/');Path('tools/verify-address-pin-comparison.mjs').write_text(s)
