"""Exercise the normal group's disclosure without editing records or starting/stopping sharing."""
import json, pathlib, re, subprocess, sys, time
import xml.etree.ElementTree as ET
serial = sys.argv[1]
adb = [r'C:/Users/TAKUMI/AppData/Local/Android/Sdk/platform-tools/adb.exe', '-s', serial]
out = pathlib.Path('output/group-details-1535') / serial
out.mkdir(parents=True, exist_ok=True)
def call(*args): return subprocess.check_output(adb + list(args))
def tree():
    for attempt in range(3):
        result = subprocess.run(adb + ['shell', 'uiautomator', 'dump', '/sdcard/pland-group.xml'], capture_output=True)
        if result.returncode == 0:
            return ET.fromstring(call('shell', 'cat', '/sdcard/pland-group.xml'))
        time.sleep(1)
    raise AssertionError('UI hierarchy unavailable')
def matches(root, label): return [n for n in root.iter('node') if label in (n.get('text'), n.get('content-desc'))]
def tap(label):
    nodes = []
    for _ in range(4):
        nodes = matches(tree(), label)
        if nodes: break
        time.sleep(1)
    assert nodes, label
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',nodes[-1].get('bounds')))
    call('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def find_scrolling(label, up=False):
    for _ in range(12):
        root=tree()
        if matches(root,label): return
        scroll=[n for n in root.iter('node') if n.get('scrollable')=='true']
        assert scroll, label
        x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll[0].get('bounds')))
        high=y1+(y2-y1)//4;low=y1+(y2-y1)*3//4
        call('shell','input','swipe',str((x1+x2)//2),str(high if up else low),str((x1+x2)//2),str(low if up else high),'350')
    raise AssertionError('Missing: '+label)
def collapsed():
    root=tree()
    assert matches(root,'通信の詳細')
    for label in ['今すぐ同期','インターネットで共有','近くの端末と共有','iPhone・PCとの通信実験']:
        assert not matches(root,label), label
tap('メニューを開く');tap('グループ')
collapsed();out.joinpath('group.png').write_bytes(call('exec-out','screencap','-p'))
tap('通信の詳細')
assert matches(tree(),'インターネットで共有')
out.joinpath('details.png').write_bytes(call('exec-out','screencap','-p'))
receipt_seen=False
for _ in range(10):
    root=tree()
    texts=[n.get('text','') for n in root.iter('node')]
    assert not any(any(word in t for word in ['通信実験','実験サーバー','実験用接続コード','実験通信を']) for t in texts), texts
    if any(t.startswith('相手が受け取った情報：') for t in texts):
        receipt_seen=True
        out.joinpath('details-bottom.png').write_bytes(call('exec-out','screencap','-p'))
        break
    scroll=[n for n in root.iter('node') if n.get('scrollable')=='true'];assert scroll
    x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll[0].get('bounds')))
    call('shell','input','swipe',str((x1+x2)//2),str(y1+(y2-y1)*3//4),str((x1+x2)//2),str(y1+(y2-y1)//4),'350')
assert receipt_seen,'Communication details end not reached'
find_scrolling('通信の詳細を閉じる',up=True);tap('通信の詳細を閉じる')
collapsed()
time.sleep(17)
collapsed()
services=call('shell','dumpsys','activity','services','io.github.nevaseme.plandmk1').decode(errors='replace')
result={'passed':True,'normalScreenHidesCommunicationControls':True,'legacyExperimentAbsentFromDetails':True,'detailsCanOpenAndClose':True,'staysCollapsedAcrossSync':True,'nearbyServiceRunning':'SharingService' in services,'noRecordEdits':True}
out.joinpath('verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False))
