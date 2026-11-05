"""Read-only real app navigation; network toggles are emulator-only and restored."""
import pathlib,sys,json,time
source=pathlib.Path('tools/ui-menu-navigation.py').read_text(encoding='utf-8').split("call('shell','am','start','-n'")[0]
source=source.replace("pathlib.Path('output/menu-1536')", "pathlib.Path('output/compact-header/android')")
exec(source)
def capture(name):
 root=tree();out.joinpath(name+'.xml').write_text(ET.tostring(root,encoding='unicode'),encoding='utf-8');out.joinpath(name+'.png').write_bytes(call('exec-out','screencap','-p'));print(name,flush=True);return root
call('shell','am','start','-n','io.github.nevaseme.plandmk1/.MainActivity');time.sleep(1);home();tap('地図')
root=capture('map');assert not find(root,'一般住民');assert not find(root,'PlanD');assert not find(root,'Tasukeai')
details_checked=False
if find(root,'詳細') and '--network-only' not in sys.argv:
 tap('詳細');root=capture('details');assert find(root,'開始');assert not find(root,'一般住民');tap('閉じる');details_checked=True
if '--network-only' not in sys.argv:
 tap('対応');tap('地域集計');capture('summary');tap('閉じる');tap('地図');tap('メニューを開く');capture('menu');tap('グループ');capture('group');home();tap('地図')
network=False
if '--network' in sys.argv:
 assert serial.startswith('emulator-')
 wifi=call('shell','settings','get','global','wifi_on').decode().strip();data=call('shell','settings','get','global','mobile_data').decode().strip()
 before=find(tree(),'無事を表示')[-1].get('bounds')
 try:
  call('shell','svc','wifi','disable');call('shell','svc','data','disable')
  for _ in range(12):
   root=tree()
   if find(root,'No internet') or find(root,'インターネット未接続'):break
   time.sleep(1)
  capture('offline');assert find(root,'No internet') or find(root,'インターネット未接続');assert find(root,'無事を表示')[-1].get('bounds')==before;network=True
 finally:
  call('shell','svc','wifi','enable' if wifi=='1' else 'disable');call('shell','svc','data','enable' if data=='1' else 'disable')
  for _ in range(12):
   root=tree()
   if not find(root,'No internet') and not find(root,'インターネット未接続'):break
   time.sleep(1)
 if wifi=='1' or data=='1':assert not find(root,'No internet') and not find(root,'インターネット未接続')
 assert find(root,'無事を表示')[-1].get('bounds')==before;capture('recovered')
checks=['Compact header']
if details_checked:checks.append('Time-only details')
if '--network-only' not in sys.argv:checks+=['Area summary in Tasks','Menu and group reachable']
out.joinpath('verification.json').write_text(json.dumps({'passed':True,'recordsEdited':False,'checks':checks,'detailsChecked':details_checked,'networkToggledAndRestored':network},ensure_ascii=False,indent=2),encoding='utf-8')
print('Compact Android header verified',flush=True)
