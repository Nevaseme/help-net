from android_device import adb_command
import subprocess,xml.etree.ElementTree as E,re,time,json,pathlib
adb = adb_command()
def tree():
    subprocess.check_output(adb+['shell','uiautomator','dump','/sdcard/pland-ui-quality.xml'])
    return E.fromstring(subprocess.check_output(adb+['shell','cat','/sdcard/pland-ui-quality.xml']))
def tap(label):
    nodes=[n for n in tree().iter('node') if label in (n.get('text'),n.get('content-desc'))]
    assert nodes, 'Control absent: '+label
    x,y,a,b=map(int,re.findall(r'\d+',nodes[-1].get('bounds')))
    subprocess.check_output(adb+['shell','input','tap',str((x+a)//2),str((y+b)//2)])
def expect(label):
    for _ in range(4):
        if any(label in (n.get('text') or '') for n in tree().iter('node')):return
        time.sleep(.5)
    raise AssertionError('Content absent: '+label)
checks=[]
tap('地図');
if any('津波の高さ' in (n.get('text') or '') for n in tree().iter('node')):tap('津波（実験）')
expect('凡例');tap('凡例');expect('スマホの安否');tap('一覧');expect('メンバー一覧');checks.append('normal app: legend to list closes overlay')
for label,content in [('対応','対応'),('安否','自分の安否'),('地図','凡例'),('一覧','メンバー一覧'),('地図','凡例')]:
    tap(label);expect(content)
    for tab in ['地図','一覧','対応','安否']:expect(tab)
    checks.append('normal app tab: '+label)
pathlib.Path('output/ui-quality/vivo/normal-navigation.json').write_text(json.dumps({'passed':True,'checks':checks,'noRecordWrites':True},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False))
