"""Read-only screen checks, temporary emulator appearance preferences restored in finally."""
import pathlib,sys,json,time
source=pathlib.Path('tools/ui-menu-navigation.py').read_text(encoding='utf-8').split("call('shell','am','start','-n'")[0]
source=source.replace("pathlib.Path('output/menu-1536')", "pathlib.Path('output/platform-quality/appearance')")
exec(source)
assert serial.startswith('emulator-')
font=call('shell','settings','get','system','font_scale').decode().strip()
night=call('shell','cmd','uimode','night').decode().strip().split(': ')[-1]
checks=[];english=False
def capture(name):
 root=tree();out.joinpath(name+'.xml').write_text(ET.tostring(root,encoding='unicode'),encoding='utf-8');out.joinpath(name+'.png').write_bytes(call('exec-out','screencap','-p'))
 print(name,flush=True)
try:
 call('shell','am','start','-n','io.github.nevaseme.plandmk1/.MainActivity');home()
 for mode in ['no','yes']:
  call('shell','cmd','uimode','night',mode);time.sleep(1)
  for tab in ['地図','一覧','対応','安否']:
   tap(tab);capture(('light' if mode=='no' else 'dark')+'-'+str(['地図','一覧','対応','安否'].index(tab)))
  tap('メニューを開く');capture(('light' if mode=='no' else 'dark')+'-menu');tap('自分の情報・住居');assert not find(tree(),'実験グループ');capture(('light' if mode=='no' else 'dark')+'-profile');home()
 checks.append('Light and dark four tabs, menu and profile; no internal group input')
 call('shell','cmd','uimode','night','no');tap('メニューを開く');tap('言語');tap('English');english=True;tap('Close')
 call('shell','settings','put','system','font_scale','2.0');time.sleep(1)
 for label in ['Map','People','Tasks','Safety']:
  tap(label);capture('large-'+label.lower())
 tap('Open menu');capture('large-menu');tap('Language');tap('日本語');english=False;tap('閉じる')
 checks.append('200% text, English four tabs and language menu reachable')
finally:
 call('shell','settings','put','system','font_scale',font);call('shell','cmd','uimode','night',night)
 if english:
  root=tree()
  if find(root,'Close'):tap('Close')
  tap('Open menu');tap('Language');tap('日本語');tap('閉じる')
out.joinpath('verification.json').write_text(json.dumps({'passed':True,'checks':checks,'restored':{'font':font,'night':night}},ensure_ascii=False,indent=2),encoding='utf-8')
