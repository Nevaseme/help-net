"""Read-only menu navigation checks on an installed app; does not edit records."""
import json,pathlib,re,subprocess,sys,time
import xml.etree.ElementTree as ET
serial=sys.argv[1]
adb=[r'C:/Users/TAKUMI/AppData/Local/Android/Sdk/platform-tools/adb.exe','-s',serial]
out=pathlib.Path('output/menu-1536')/serial;out.mkdir(parents=True,exist_ok=True)
def call(*args):return subprocess.check_output(adb+list(args))
def tree():
    for _ in range(4):
        r=subprocess.run(adb+['shell','uiautomator','dump','/sdcard/pland-menu.xml'],capture_output=True)
        if r.returncode==0:return ET.fromstring(call('shell','cat','/sdcard/pland-menu.xml'))
        time.sleep(.5)
    raise AssertionError('UI unavailable')
def find(root,label):return [n for n in root.iter('node') if label in (n.get('text'),n.get('content-desc'))]
def tap(label):
    for _ in range(12):
        root=tree();nodes=find(root,label)
        if nodes:
            x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[-1].get('bounds')))
            if y2>y1:
                call('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4);return
        scroll=[n for n in root.iter('node') if n.get('scrollable')=='true']
        if not scroll:time.sleep(.5);continue
        x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll[0].get('bounds')))
        call('shell','input','swipe',str((x1+x2)//2),str(y1+(y2-y1)*3//4),str((x1+x2)//2),str(y1+(y2-y1)//4),'250')
    raise AssertionError('Missing '+label)
def home():
    for _ in range(4):
        root=tree()
        if find(root,'メニューを開く'):return
        if find(root,'戻る'):tap('戻る')
        else:call('shell','input','keyevent','4')
    raise AssertionError('No main screen')
call('shell','am','start','-n','io.github.nevaseme.plandmk1/.MainActivity');time.sleep(1)
home();tap('メニューを開く')
assert find(tree(),'登録情報')
out.joinpath('menu.png').write_bytes(call('exec-out','screencap','-p'))
if '--capture' in sys.argv:
    tap('アプリ情報');assert find(tree(),'アプリ情報');home();tap('メニューを開く');assert find(tree(),'登録情報')
    out.joinpath('menu.png').write_bytes(call('exec-out','screencap','-p'));tap('閉じる');print('Menu captured; last destination reachable.');sys.exit(0)
root=tree();texts=[n.get('text','') for n in root.iter('node')]
assert '登録情報' in texts
assert '画面デモ' not in texts
tap('閉じる');assert find(tree(),'メニューを開く')
destinations=['自分の情報・住居','グループ','建物・住戸','災害・訓練','オフライン地図','位置記録','言語','アプリ情報']
for label in destinations:
    home();tap('メニューを開く');tap(label)
    root=tree();assert not find(root,'登録情報'),label
    if label=='言語':
        assert find(root,'日本語') and find(root,'English');tap('閉じる')
    else:assert find(root,label),label
home();tap('メニューを開く')
out.joinpath('menu.png').write_bytes(call('exec-out','screencap','-p'))
tap('アプリ情報');home();tap('メニューを開く');call('shell','input','keyevent','4');assert not find(tree(),'登録情報')
result={'passed':True,'destinations':destinations,'closeAndBack':True,'recordsUnchanged':True}
out.joinpath('verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(result,ensure_ascii=False))
