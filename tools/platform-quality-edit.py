from pathlib import Path
def edit(file,pairs):
 p=Path(file);s=p.read_text(encoding='utf-8')
 for old,new in pairs:
  assert old in s,(file,old[:80]);s=s.replace(old,new)
 p.write_text(s,encoding='utf-8')
ui='android/app/src/main/java/io/github/nevaseme/plandmk1/ui/'
edit(ui+'PlanApp.kt',[
 ('    var groupInput by rememberSaveable(group) { mutableStateOf(group) }\n',''),
 ('        item { OutlinedTextField(groupInput, { groupInput = it }, label = { Text("実験グループ") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()) }\n',''),
 ('model.saveProfile(groupInput, name, lat, lon, floor, room)','model.saveProfile(group, name, lat, lon, floor, room)')])
edit(ui+'IncidentComponents.kt',[('Text(uiText("earthquake"))','Text(uiText("report_earthquake"))')])
for lang,label in [('values','地震の発生を報告'),('values-en','Report an earthquake')]:
 edit(f'android/app/src/main/res/{lang}/approved_ui.xml',[('</resources>',f'    <string name="approved_report_earthquake">{label}</string>\n</resources>')])
edit('android/app/src/main/res/values-en/approved_ui.xml',[('>Resident list</string>','>People</string>'),('>Response</string>','>Tasks</string>'),('>My status</string>','>Safety</string>')])
edit(ui+'theme/Theme.kt',[('darkTheme: Boolean = false','darkTheme: Boolean = isSystemInDarkTheme()'),('secondary = Color(0xFFBED7E8),','secondary = Color(0xFFBED7E8), onPrimary=Color(0xFF082D63),\n    primaryContainer=Color(0xFF213E64),onPrimaryContainer=Color(0xFFD9E7FF), outline=Color(0xFF8FA4B2),outlineVariant=Color(0xFF617786),')])
# Map annotation/risk colours are deliberately not transformed.
mapping={'Color(0xFF536478)':'MaterialTheme.colorScheme.onSurfaceVariant','Color(0xFF697586)':'MaterialTheme.colorScheme.onSurfaceVariant','Color(0xFF183447)':'MaterialTheme.colorScheme.onSurface','Color(0xFFF3F7FB)':'MaterialTheme.colorScheme.surfaceContainerLow','Color(0xFFF3F6F9)':'MaterialTheme.colorScheme.surfaceContainerLow','Color(0xFFF5F8FB)':'MaterialTheme.colorScheme.surfaceContainerLow','Color(0xFFF1F5F9)':'MaterialTheme.colorScheme.surfaceContainerHigh','Color(0xFFF2F6FF)':'MaterialTheme.colorScheme.primaryContainer','Color(0xFFD6DEE7)':'MaterialTheme.colorScheme.outlineVariant','Color(0xFFD6E0E8)':'MaterialTheme.colorScheme.outlineVariant'}
for f in ['ApprovedComponents.kt','PlanApp.kt','ResponseScreen.kt','SituationScreen.kt']:
 p=Path(ui+f);s=p.read_text(encoding='utf-8')
 for old,new in mapping.items():
  # Keep foreground on the fixed hazard palette dark in both themes.
  lines=s.splitlines();s='\n'.join(line if 'when (home.rating)' in line or 'when(home.rating)' in line else line.replace(old,new) for line in lines)+'\n'
 s=s.replace('color=Color.White','color=MaterialTheme.colorScheme.surface').replace('color = Color.White','color = MaterialTheme.colorScheme.surface').replace('.background(Color.White)', '.background(MaterialTheme.colorScheme.surface)').replace('else Color.White','else MaterialTheme.colorScheme.surface')
 # White switch thumb and selected palette text remain meaningful foregrounds.
 if f=='ApprovedComponents.kt':s=s.replace('.background(MaterialTheme.colorScheme.surface,androidx.compose.foundation.shape.CircleShape)', '.background(Color.White,androidx.compose.foundation.shape.CircleShape)')
 if f=='SituationScreen.kt':s=s.replace('heightIn(min=44.dp)','heightIn(min=48.dp)').replace('selectedLabelColor=Color(0xFF155EEF)','selectedLabelColor=MaterialTheme.colorScheme.primary')
 p.write_text(s,encoding='utf-8')
edit(ui+'ResponseScreen.kt',[
 ('import androidx.compose.runtime.saveable.rememberSaveable','import androidx.compose.runtime.saveable.rememberSaveable\nimport androidx.compose.runtime.saveable.rememberSaveableStateHolder\nimport androidx.compose.runtime.saveable.Saver'),
 ('var selected by remember(group,incidentId){mutableStateOf<ResponseTarget?>(null)}','var selected by rememberSaveable(group,incidentId,stateSaver=Saver<ResponseTarget?,ArrayList<String>>(save={it?.let {arrayListOf(it.kind.name,it.id)}?:arrayListOf()},restore={if(it.isEmpty())null else ResponseTarget(TargetKind.valueOf(it[0]),it[1])})){mutableStateOf<ResponseTarget?>(null)}'),
 ('var editor by remember(group,incidentId)', 'val drafts=rememberSaveableStateHolder()\n    var editor by rememberSaveable(group,incidentId)'),
 ('var materialId by remember {','var materialId by rememberSaveable {'),
 ('ResponseEditor(mode,case,point,materialId,heads,history,roster,member,coordinator,editable,model){editor=null}', 'val draftKey="$group:$incidentId:$mode:${selected?.kind}:${selected?.id}:$materialId"\n            drafts.SaveableStateProvider(draftKey) { ResponseEditor(mode,case,point,materialId,heads,history,roster,member,coordinator,editable,model,onSaved={drafts.removeState(draftKey);editor=null}){editor=null} }'),
 ('model:PlanViewModel,close:()->Unit){','model:PlanViewModel,onSaved:()->Unit,close:()->Unit){'),
 ('model.saveResponse(change,replaced,close)','model.saveResponse(change,replaced,onSaved)'),
 ('val reviewedHeads=remember {currentHeads.toList()}','var reviewedIds by rememberSaveable { mutableStateOf(currentHeads.map {it.eventId}) }\n    val reviewedHeads=currentHeads.filter {it.eventId in reviewedIds}'),
 ('currentHeads.map {it.eventId}.toSet()!=reviewedHeads.map {it.eventId}.toSet()','currentHeads.map {it.eventId}.toSet()!=reviewedIds.toSet()'),
 ('if(editorUpdated)Text(uiText("editor_updated"),color=MaterialTheme.colorScheme.error)','if(editorUpdated) {\n            Text(uiText("editor_updated"),color=MaterialTheme.colorScheme.error)\n            currentHeads.forEach { ResponseEventText(it.change) }\n            TextButton(onClick={reviewedIds=currentHeads.map {it.eventId};reviewed=false}) {Text(uiText("review_current_heads"))}\n        }')])
edit('android/app/build.gradle.kts',[('versionCode = 49','versionCode = 50'),('versionName = "1.5.36"','versionName = "1.5.37"')])
edit('lab/web/web1617-ui.mjs',[("'一覧':'Resident list'","'一覧':'People'"),("'対応':'Response'","'対応':'Tasks'"),("'安否':'My status'","'安否':'Safety'"),("row.progress.length===1&&row.progress[0].change.status===v?", "(row.progress.length===0&&v==='UNASSIGNED'||row.progress.length===1&&row.progress[0].change.status===v)?")])
for f in ['lab/web/app.mjs','lab/web/index.html']:edit(f,[('1.6.10','1.6.11')])
