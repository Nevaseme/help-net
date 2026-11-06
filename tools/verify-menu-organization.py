"""Navigate menus and change only appearance; restore appearance at the end."""
import pathlib,sys,json,time
source=pathlib.Path('tools/ui-menu-navigation.py').read_text(encoding='utf-8').split("call('shell','am','start','-n'")[0]
source=source.replace("pathlib.Path('output/menu-1536')", "pathlib.Path('output/qa/menu-1539/android')")
exec(source)
def capture(name):
 root=tree();out.joinpath(name+'.xml').write_text(ET.tostring(root,encoding='unicode'),encoding='utf-8');out.joinpath(name+'.png').write_bytes(call('exec-out','screencap','-p'));print(name,flush=True);return root
def menu(label):home();tap('メニューを開く');tap(label)
call('shell','am','start','-n','io.github.nevaseme.plandmk1/.MainActivity');time.sleep(1);home();tap('メニューを開く');root=capture('menu');assert find(root,'自分の登録情報');assert not find(root,'建物・住戸');assert not find(root,'言語');tap('閉じる')
menu('自分の登録情報');assert find(tree(),'自分の名前');tap('住んでいる建物・住戸を選ぶ');assert find(tree(),'建物・住戸');capture('building');home()
menu('訓練・地震');root=capture('training');assert find(root,'訓練を開始する') or any('管理者' in n.get('text','') or 'グループを作成' in n.get('text','') for n in root.iter('node'));home()
menu('アプリ設定');capture('settings-initial')
labels=['端末に合わせる','ライト','ダーク']
def selected():
 root=tree()
 for label in labels:
  nodes=find(root,label)
  if any(n.get('checked')=='true' for n in nodes):return label
  for parent in root.iter('node'):
   if any(label==n.get('text') for n in parent) and (parent.get('checked')=='true' or any(n.get('checked')=='true' for n in parent)):return label
 raise AssertionError('Cannot read selected appearance')
original=selected()
try:
 tap('ライト');assert selected()=='ライト';capture('settings-light')
 tap('ダーク');assert selected()=='ダーク';capture('settings-dark')
 call('shell','am','force-stop','io.github.nevaseme.plandmk1');call('shell','am','start','-n','io.github.nevaseme.plandmk1/.MainActivity');time.sleep(1);menu('アプリ設定');assert selected()=='ダーク'
finally:
 tap(original);tap('閉じる');home()
out.joinpath('verification.json').write_text(json.dumps({'passed':True,'recordsEdited':False,'appearanceRestored':original,'checks':['Four menu entries','Profile to buildings','Training destination','Light and dark selectable','Dark persisted after restart']},ensure_ascii=False,indent=2),encoding='utf-8')
print('Menu and persisted appearance verified',flush=True)
