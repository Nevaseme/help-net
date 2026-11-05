from pathlib import Path
import re
p=Path('lab/web/style.css');s=p.read_text(encoding='utf-8')
# Replace literal surface colours, leaving map hazard colours and status symbols intact.
head,body=s.split('*{box-sizing',1)
for old,new in {'#536478':'var(--muted)','#697586':'var(--muted)','#7b8996':'var(--muted)','#f3f7fb':'var(--surface)','#f5f8fb':'var(--surface)','#f3f6fa':'var(--surface)','#f4f7fb':'var(--surface)','#edf0f3':'var(--surface)','#edf2f5':'var(--surface)','#e8edef':'var(--surface)','#f2f6ff':'var(--selected)','#edf6ff':'var(--selected)','#eff9f7':'var(--safe-surface)','#fff1f1':'var(--danger-surface)','#fff0ee':'var(--danger-surface)','#fff7e6':'var(--warning-surface)','#fff8e8':'var(--warning-surface)','#805600':'var(--warning-text)','#a46b00':'var(--warning-text)','#7a590d':'var(--warning-text)','#b42318':'var(--danger)','#e1e5ea':'var(--line)','#cbd5df':'var(--line)'}.items():body=re.sub(re.escape(old)+r'(?![0-9a-fA-F])',new,body)
body=re.sub(r'background(?:-color)?:(?:#fff(?:fff)?|white)(?=[;}])','background:var(--paper)',body)
body=body.replace('color:#fff','color:var(--on-primary)').replace('color:white','color:var(--on-primary)')
head=head.replace('color-scheme:light;','color-scheme:light dark;--paper:#fff;--on-primary:#fff;--selected:#f2f6ff;--safe-surface:#eff9f7;--danger-surface:#fff1f1;--warning-surface:#fff7e6;--warning-text:#805600;').replace('background:#fff;','background:var(--paper);')
s=head+'*{box-sizing'+body
s+='''
/* Platform quality: readable labels, generous targets, and system appearance. */
.app-nav button{overflow-wrap:normal;word-break:normal;hyphens:none;min-width:0;padding-inline:2px}
.map-legend summary{min-height:48px;min-width:48px;display:flex;align-items:center;justify-content:center}
.icon-button{min-width:48px;min-height:48px}
.update-notice{position:fixed;z-index:150;left:12px;right:12px;bottom:calc(var(--nav-height,72px) + env(safe-area-inset-bottom,0px) + 12px);max-width:640px;margin:auto;display:flex;align-items:center;gap:16px;padding:12px 16px;border:1px solid var(--line);border-radius:12px;background:var(--paper);color:var(--ink);box-shadow:0 4px 18px #0002;font-size:.875rem}
.update-notice button{flex-shrink:0}
@media(prefers-color-scheme:dark){
 :root{--paper:#142630;--surface:#1b303c;--ink:#e4eff7;--muted:#bdcdd8;--primary:#aac7ff;--on-primary:#082d63;--line:#617786;--selected:#213e64;--danger:#ffb4ab;--safe:#79d7c1;--safe-surface:#173f38;--danger-surface:#482a30;--warning-surface:#42351a;--warning-text:#f4ce7a;background:#10212b}
 .app-nav,.app-header,.menu-sheet,.design-sheet,main,input,textarea,select{background:var(--paper);color:var(--ink)}
 .app-nav img,.menu-items img,.row-chevron,.icon-button img,.unknown-row img{filter:brightness(0) saturate(100%) invert(87%) sepia(7%) saturate(350%) hue-rotate(165deg)}
 .app-nav button[aria-pressed="true"] img{filter:brightness(0) saturate(100%) invert(80%) sepia(28%) saturate(715%) hue-rotate(182deg)}
 .map-legend,.map-legend[open]>summary,.map-calculation,.leaflet-bar a,.leaflet-bar a:hover,.leaflet-control-attribution{background:var(--paper);color:var(--ink)}
 #map-tsunami[aria-pressed="true"]{background:#aac7ff22;border-color:#aac7ff88;color:var(--primary)}
 .person-symbol.SOS,.person-symbol.SAFE,.person-symbol.RESCUED,.person-rescue{color:#fff}
 button.danger{color:#482a30}
 .safety-actions #safe,.safety-actions #sos{color:var(--ink)}
 input,textarea,select{border-color:var(--line)}
 .sheet-scrim{background:#0008}
}
@media(prefers-reduced-motion:reduce){*,*::before,*::after{scroll-behavior:auto!important;transition:none!important;animation:none!important}}
'''
p.write_text(s,encoding='utf-8')
ui=Path('android/app/src/main/java/io/github/nevaseme/plandmk1/ui')
p=ui/'ResponseScreen.kt';s=p.read_text(encoding='utf-8').replace('Color(0xFFFFF8E8)','MaterialTheme.colorScheme.tertiaryContainer').replace('Color(0xFF805600)','MaterialTheme.colorScheme.onTertiaryContainer').replace('Color(0xFFEFF9F7)','MaterialTheme.colorScheme.secondaryContainer');p.write_text(s,encoding='utf-8')
p=ui/'SituationScreen.kt';s=p.read_text(encoding='utf-8').replace('Color(0xFFFFF0F3)','MaterialTheme.colorScheme.errorContainer').replace('Color(0xFF7A2537)','MaterialTheme.colorScheme.onErrorContainer').replace('containerColor = Color(0xFF00836C)','containerColor = Color(0xFF00836C),contentColor=Color.White');p.write_text(s,encoding='utf-8')
