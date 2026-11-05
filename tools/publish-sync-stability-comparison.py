from pathlib import Path
import shutil

source=Path('C:/Users/TAKUMI/.codex/visualizations/2026/10/24/01a16c0c-f86e-7220-ad05-e96afa7746d4/sync-1544/android')
assets=Path('demo/assets/sync-1544');assets.mkdir(parents=True,exist_ok=True)
for name in ['group-before','group-after','settings','training']:
 shutil.copyfile(source/(name+'.png'),assets/(name+'.png'))
column='''<section class="sharing-view" id="sync-stable-android"><div class="comparison-caption"><p class="comparison-label">Android 1.5.44 · 自動同期時の表示を安定化</p><p class="comparison-summary">自動同期を続けながら、入力欄とボタンの状態・配置を維持します。グループ・登録情報・訓練・設定・主要4タブで確認しました。</p></div><figure class="pwa-screen"><img src="assets/sync-1544/group-after.png" alt="自動同期後も入力欄の配置と操作状態を保つグループ画面"><figcaption>Android実装画面・架空グループ</figcaption></figure><details><summary>自動同期前の画面</summary><img src="assets/sync-1544/group-before.png" alt="自動同期前の同じグループ画面" style="width:100%;height:auto"></details><details><summary>アプリ設定</summary><img src="assets/sync-1544/settings.png" alt="同期中も配置を維持するアプリ設定" style="width:100%;height:auto"></details><details><summary>訓練・地震</summary><img src="assets/sync-1544/training.png" alt="同期中も操作できる訓練画面" style="width:100%;height:auto"></details></section>'''
for folder in [Path('demo'),Path('.implementation/cloud-demo-publish/demo')]:
 p=folder/'cloud-sharing.html';s=p.read_text(encoding='utf-8')
 if 'id="sync-stable-android"' not in s:p.write_text(s.replace('</main>',column+'</main>'),encoding='utf-8')
shutil.copytree(assets,Path('.implementation/cloud-demo-publish/demo/assets/sync-1544'),dirs_exist_ok=True)
