from pathlib import Path
import shutil
evidence=Path('output/qa/menu-1539')
out=Path('demo/assets/menu-organization-1539');out.mkdir(parents=True,exist_ok=True)
for platform in ['android','pwa']:
    for name in ['menu','settings-light','training']:
        src=evidence/'android/emulator-5554'/f'{name}.png' if platform=='android' else evidence/f'pwa-{name}.png'
        shutil.copyfile(src,out/f'{platform}-{name}.png')
columns=''
for platform,version,label in [('android','1.5.39','Android'),('pwa','1.6.13','PWA')]:
    columns+=f'''<section class="sharing-view" id="organized-{platform}-1539"><div class="comparison-caption"><p class="comparison-label">{label} {version} · メニューと設定</p><p class="comparison-summary">登録情報・グループ・訓練・アプリ設定の4つの入口へ整理。建物と住戸は登録情報にまとめ、ライト／ダークの選択と保存に対応しました。</p></div><figure class="pwa-screen"><img src="assets/menu-organization-1539/{platform}-menu.png" alt="{label}の4項目のメニュー"><figcaption>実装画面・架空データによる確認</figcaption></figure><details><summary>アプリ設定を見る</summary><img src="assets/menu-organization-1539/{platform}-settings-light.png" alt="ライト・ダーク・端末に合わせるを選べる{label}の設定" style="width:100%;height:auto"></details><details><summary>訓練・地震の画面を見る</summary><img src="assets/menu-organization-1539/{platform}-training.png" alt="{label}の訓練への入口" style="width:100%;height:auto"></details></section>'''
for folder in [Path('demo'),Path('.implementation/cloud-demo-publish/demo')]:
    p=folder/'cloud-sharing.html';s=p.read_text(encoding='utf-8');assert 'id="organized-android-1539"' not in s
    s=s.replace('</main>',columns+'</main>');p.write_text(s,encoding='utf-8')
shutil.copytree(out,Path('.implementation/cloud-demo-publish/demo/assets/menu-organization-1539'),dirs_exist_ok=True)
print('Added two columns, preserving all prior comparisons. Only fictional emulator/PWA images included.')
