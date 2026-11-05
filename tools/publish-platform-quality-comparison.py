"""Append the verified platform-quality screens without changing historical columns."""
from pathlib import Path
import shutil

assets = Path('demo/assets/platform-quality-1537')
assets.mkdir(parents=True, exist_ok=True)
sources = {
    'android-light': 'output/platform-quality/appearance/emulator-5554/light-menu.png',
    'android-dark': 'output/platform-quality/appearance/emulator-5554/dark-menu.png',
    'android-large': 'output/platform-quality/appearance/emulator-5554/large-menu.png',
    'android-profile': 'output/platform-quality/appearance/emulator-5554/light-profile.png',
    'pwa-light': 'output/platform-quality/pwa/light-menu.png',
    'pwa-dark': 'output/platform-quality/pwa/dark-menu.png',
    'pwa-large': 'output/platform-quality/pwa/large-english.png',
    'pwa-draft': 'output/platform-quality/pwa/draft.png',
}
for name, source in sources.items():
    shutil.copyfile(source, assets / (name + '.png'))
page = Path('demo/cloud-sharing.html')
old = page.read_text(encoding='utf-8')
assert 'id="quality-android-1537"' not in old
columns = []
for platform, version, label, detail in [
    ('android', '1.5.37', 'Android', '入力の途中でタブを切り替えても下書きを保持。プロフィールの内部IDを非表示にし、地震の操作名を明確にしました。'),
    ('pwa', '1.6.11', 'PWA', '編集中の下書きを保持。更新の案内から入力を保護して新版へ移行できます。凡例のタッチ領域も拡大しました。'),
]:
    more = 'profile' if platform == 'android' else 'draft'
    more_label = '自分の情報・住居' if platform == 'android' else '対応状況の編集'
    columns.append(f'''<section class="sharing-view" id="quality-{platform}-1537">
<div class="comparison-caption" style="height:auto;min-height:140px"><p class="comparison-label">{label} {version} · 読みやすさと入力の保護</p>
<p class="comparison-summary">{detail} OSの明暗設定に対応し、文字拡大時のタブも読みやすくしました。</p></div>
<figure class="pwa-screen"><img src="assets/platform-quality-1537/{platform}-light.png" alt="{label}の明るい表示のメニュー"><figcaption>実装画面・架空データで確認</figcaption></figure>
<details><summary>暗い表示を見る</summary><img src="assets/platform-quality-1537/{platform}-dark.png" alt="{label}の暗い表示のメニュー" style="width:100%;height:auto"></details>
<details><summary>文字を200%に拡大した画面を見る</summary><img src="assets/platform-quality-1537/{platform}-large.png" alt="{label}の英語・文字200%表示" style="width:100%;height:auto"></details>
<details><summary>{more_label}を見る</summary><img src="assets/platform-quality-1537/{platform}-{more}.png" alt="{label}の{more_label}" style="width:100%;height:auto"></details></section>''')
updated = old.replace('</main>', ''.join(columns) + '</main>')
assert updated.replace(''.join(columns), '') == old
updated = updated.replace('aria-label="比較する画面">', 'aria-label="比較する画面"><a href="#quality-android-1537">最新Android 1.5.37</a><a href="#quality-pwa-1537">最新PWA 1.6.11</a>')
updated = updated.replace('>最新PWA 1.6.3<', '>PWA 1.6.3<').replace('>最新Android 1.5.25<', '>Android 1.5.25<')
page.write_text(updated, encoding='utf-8')
destination = Path('.implementation/cloud-demo-publish/demo')
shutil.copyfile(page, destination / page.name)
shutil.copytree(assets, destination / 'assets/platform-quality-1537', dirs_exist_ok=True)
print('Appended Android and PWA columns; all prior columns preserved.')
