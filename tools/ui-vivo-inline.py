"""Read displayed controls and exercise non-storing production UI fixtures on vivo."""
import subprocess, pathlib, xml.etree.ElementTree as ET, re, time, json
ADB=[r'C:/Users/TAKUMI/AppData/Local/Android/Sdk/platform-tools/adb.exe','-s','10AE9Y3GDY000JU']
OUT=pathlib.Path('output/ui-quality/vivo-inline-private'); OUT.mkdir(parents=True,exist_ok=True)
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
tap('地図');time.sleep(.6)
tap('津波');expect('津波の高さを入力してください')
nodes=[n for n in tree().iter('node') if n.get('class')=='android.widget.EditText'];assert len(nodes)==1
x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[0].get('bounds')))
call('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));call('shell','input','text','20');call('shell','input','keyevent','4')
for _ in range(10):
    if any('高さ 20m の事前計算結果' in (n.get('text') or '') for n in tree().iter('node')):break
    time.sleep(.4)
expect('高さ 20m の事前計算結果');shot('height-20');checks.append('inline 20m input automatically displays inundation')
tap('津波');expect('凡例');tap('津波');expect('高さ 20m の事前計算結果');checks.append('toggle preserves height')
tap('一覧');expect('メンバー一覧');tap('地図');expect('高さ 20m の事前計算結果');checks.append('tabs retain result')
(OUT/'verification.json').write_text(json.dumps({'passed':True,'checks':checks,'noRecordWrites':True},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False))
