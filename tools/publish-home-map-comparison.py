from pathlib import Path
import shutil
evidence=Path('C:/Users/TAKUMI/.codex/visualizations/2026/10/24/01a16c0c-f86e-7220-ad05-e96afa7746d4/home-1540')
out=Path('demo/assets/home-map-1540');out.mkdir(parents=True,exist_ok=True)
for name in ['map','home-picker','group']:
 shutil.copyfile(evidence/'android/emulator-5554'/f'{name}.png',out/f'android-{name}.png')
shutil.copyfile(evidence/'pwa/group.png',out/'pwa-group.png')
columns='''<section class="sharing-view" id="home-map-android-1540"><div class="comparison-caption"><p class="comparison-label">Android 1.5.40 · 住居選択と地図</p><p class="comparison-summary">住居選択は端末の現在地から開始。地図上の常時説明枠と、一般メンバーの「参加者」表示を削除しました。</p></div><figure class="pwa-screen"><img src="assets/home-map-1540/android-map.png" alt="説明枠を除いたAndroidの地図"><figcaption>実装画面・架空データによる確認</figcaption></figure><details><summary>現在地からの住居選択</summary><img src="assets/home-map-1540/android-home-picker.png" alt="エミュレーターの現在地から始まる住居選択" style="width:100%;height:auto"></details><details><summary>グループ表示</summary><img src="assets/home-map-1540/android-group.png" alt="参加者ラベルを除いたグループ" style="width:100%;height:auto"></details></section><section class="sharing-view" id="home-map-pwa-1540"><div class="comparison-caption"><p class="comparison-label">PWA 1.6.14 · グループ表示</p><p class="comparison-summary">一般メンバーの役割と保存済みの補助表示を除き、グループ名と自分の名前を表示。管理者の場合は役割を残しています。</p></div><figure class="pwa-screen"><img src="assets/home-map-1540/pwa-group.png" alt="グループ名と自分の名前を表示するPWA"><figcaption>公開PWAのグループ画面上部・架空データによる確認</figcaption></figure></section>'''
for folder in [Path('demo'),Path('.implementation/cloud-demo-publish/demo')]:
 p=folder/'cloud-sharing.html';s=p.read_text(encoding='utf-8');assert 'id="home-map-android-1540"' not in s
 p.write_text(s.replace('</main>',columns+'</main>'),encoding='utf-8')
shutil.copytree(out,Path('.implementation/cloud-demo-publish/demo/assets/home-map-1540'),dirs_exist_ok=True)
print('Appended two comparison columns with fictional images only')
