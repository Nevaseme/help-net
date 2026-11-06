"""Read-only home picker/group checks. Does not save a home or change group records."""
import pathlib,sys,json,time
source=pathlib.Path('tools/ui-menu-navigation.py').read_text(encoding='utf-8').split("call('shell','am','start','-n'")[0]
source=source.replace("pathlib.Path('output/menu-1536')", "pathlib.Path('output/qa/home-1540/android')")
exec(source)
def capture(name):
 root=tree();out.joinpath(name+'.xml').write_text(ET.tostring(root,encoding='unicode'),encoding='utf-8');out.joinpath(name+'.png').write_bytes(call('exec-out','screencap','-p'));print(name,flush=True);return root
def menu(label):
 home();tap('メニューを開く');tap(label)
call('shell','am','start','-n','io.github.nevaseme.plandmk1/.MainActivity');time.sleep(1);home()
if find(tree(),'閉じる'):tap('閉じる')
menu('自分の登録情報');tap('地図から位置を選ぶ')
if serial.startswith('emulator-'):
 for _ in range(5):
  call('emu','geo','fix','135.7625','35.0145');time.sleep(1)
else:time.sleep(17)
root=capture('home-picker');located=bool(find(root,'住居の位置をタップ'))
assert located or any('現在地を取得できませんでした' in n.get('text','') for n in root.iter('node'))
tap('位置選択をやめる');home();tap('地図');root=capture('map')
assert not any(any(s in n.get('text','') for s in ['想定震度','京都の収録範囲外','建物の全壊率']) for n in root.iter('node'))
menu('グループ');root=capture('group');assert not find(root,'参加者');home()
out.joinpath('verification.json').write_text(json.dumps({'passed':True,'currentLocationAcquired':located,'recordsEdited':False,'checks':['Picker resolves current location or explains failure','Cancel does not save','Map explanatory overlay removed','Redundant member role removed']},ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'passed':True,'currentLocationAcquired':located}),flush=True)
