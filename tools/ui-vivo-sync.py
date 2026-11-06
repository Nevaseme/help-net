"""Read-only observation of the already-open sharing screen, including automatic refresh."""
from android_device import adb_command
import collections, io, json, pathlib, re, subprocess, sys, time
import xml.etree.ElementTree as ET
sys.path.insert(0, str(pathlib.Path('.implementation/tsunami-plot-deps').resolve()))
from PIL import Image

ADB = adb_command()
OUT = pathlib.Path('output/sync-inspection-private')
samples, states = [], []
start, last_tree = time.monotonic(), -100
while time.monotonic() - start < 55:
    if time.monotonic() - last_tree > 6:
        subprocess.run(ADB + ['shell', 'uiautomator', 'dump', '/sdcard/pland-sync-check.xml'], capture_output=True, timeout=15, check=True)
        nodes = list(ET.fromstring(subprocess.check_output(ADB + ['shell', 'cat', '/sdcard/pland-sync-check.xml'])).iter('node'))
        buttons = [n for n in nodes if n.get('text') == '今すぐ同期']
        assert buttons, 'Sharing screen disappeared'
        box = tuple(map(int, re.findall(r'\d+', buttons[0].get('bounds'))))
        states.append({'bounds': box, 'status': [n.get('text') for n in nodes if n.get('text') in ['同期しています…', '未送信の情報があります', '同期済み']]})
        last_tree = time.monotonic()
    png = subprocess.check_output(ADB + ['exec-out', 'screencap', '-p'])
    pixels = Image.open(io.BytesIO(png)).convert('RGB').crop(box)
    dominant, _ = collections.Counter(pixels.getdata()).most_common(1)[0]
    samples.append({'elapsed': round(time.monotonic() - start, 2), 'color': dominant})
    if len(samples) == 1:
        OUT.joinpath('vivo-steady-button.png').write_bytes(png)
    time.sleep(.25)
result = {
    'passed': len({s['color'] for s in samples}) == 1 and len({s['bounds'] for s in states}) == 1,
    'durationSeconds': round(time.monotonic() - start, 2), 'sampleCount': len(samples),
    'colors': list({s['color'] for s in samples}), 'states': states,
}
OUT.joinpath('vivo-button-color.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False))
assert result['passed']
