"""Read displayed controls and exercise non-storing production UI fixtures on vivo."""
import subprocess, pathlib, xml.etree.ElementTree as ET, re, time, json
ADB=[r'C:/Users/TAKUMI/AppData/Local/Android/Sdk/platform-tools/adb.exe','-s','10AE9Y3GDY000JU']
OUT=pathlib.Path('output/ui-quality/vivo'); OUT.mkdir(parents=True,exist_ok=True)
def call(*args):return subprocess.check_output(ADB+list(args))
def tree():
    call('shell','uiautomator','dump','/sdcard/pland-ui-quality.xml')
    return ET.fromstring(call('shell','cat','/sdcard/pland-ui-quality.xml'))
def tap(label):
    nodes=[n for n in tree().iter('node') if label in (n.get('text'),n.get('content-desc'))]
    if not nodes:raise AssertionError('Missing control: '+label)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[-1].get('bounds')))
    call('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.35)
def expect(label):
    if not any(label in (n.get('text') or '') for n in tree().iter('node')):raise AssertionError('Missing content: '+label)
def shot(name):
    (OUT/(name+'.png')).write_bytes(call('exec-out','screencap','-p'))
checks=[]
shot('people');expect('メンバー一覧');checks.append('resident list and statistics')
tap('山田 太郎');expect('山田 太郎');shot('person');checks.append('resident details')
tap('安否');expect('自分の安否');shot('safety');checks.append('detail to safety tab')
tap('地図');time.sleep(2);shot('map');tap('凡例');expect('凡例');shot('legend');checks.append('map and legend')
tap('閉じる');tap('一覧');expect('メンバー一覧');checks.append('legend close and list tab')
tap('メニューを開く');shot('menu');tap('設定');expect('言語');shot('settings');checks.append('menu and settings')
# Keep the user's Japanese preference; no language or registration data is changed.
tap('閉じる');tap('一覧');expect('メンバー一覧');checks.append('settings close and return')
(OUT/'verification.json').write_text(json.dumps({'checks':checks,'passed':True,'fixture':'non-storing production components','device':'vivo','version':'1.5.25'},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False))
call('shell','am','start','-n','io.github.nevaseme.plandmk1/.MainActivity')
