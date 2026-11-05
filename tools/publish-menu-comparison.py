from pathlib import Path
import shutil
out=Path('demo/assets/menu-navigation-1536');out.mkdir(parents=True,exist_ok=True)
for source,name in [('output/menu-1536/emulator-5554/menu.png','android.png'),('output/menu-1536/emulator-5554/menu-large.png','android-large.png'),('output/menu-1536/pwa/menu-400-1-ja.png','pwa.png'),('output/menu-1536/pwa/menu-320-2-en.png','pwa-large.png')]:
    shutil.copyfile(source,out/name)
p=Path('demo/cloud-sharing.html');s=p.read_text(encoding='utf-8')
assert 'id="menu-android-1536"' not in s
columns=''
for platform,version,label in [('android','1.5.36','Android'),('pwa','1.6.10','PWA')]:
    columns+=f'''<section class="sharing-view" id="menu-{platform}-1536"><div class="comparison-caption"><p class="comparison-label">{label} {version} · メニューの整理</p><p class="comparison-summary">登録情報・災害への備え・アプリ設定の3区分へ整理。重複した入口を減らし、閉じるボタンと読みやすい項目配置を統一しました。</p></div><figure class="pwa-screen"><img src="assets/menu-navigation-1536/{platform}.png" alt="3区分に整理した{label}のメニュー"><figcaption>実装画面・架空データによる確認</figcaption></figure><details><summary>文字を拡大した画面を見る</summary><img src="assets/menu-navigation-1536/{platform}-large.png" alt="文字を拡大しスクロールで操作できる{label}のメニュー" style="width:100%;height:auto"></details></section>'''
s=s.replace('</main>',columns+'</main>');p.write_text(s,encoding='utf-8')
dest=Path('.implementation/cloud-demo-publish/demo')
shutil.copyfile(p,dest/'cloud-sharing.html');shutil.copytree(out,dest/'assets/menu-navigation-1536',dirs_exist_ok=True)
print('Appended two comparison columns; previous columns preserved.')
