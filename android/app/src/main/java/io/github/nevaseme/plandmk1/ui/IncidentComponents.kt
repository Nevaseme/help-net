package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.PlanViewModel

@Composable fun IncidentSelector(model: PlanViewModel) {
    val state by model.incidentUi.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val current=state.current
    TextButton(enabled=!saving,onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {
        Text(if(current!=null) "表示中の${if(current.start.training) "訓練" else "地震"}：${sampleIncidentTitle(current.start.groupId,current.start.title)}／対象地域：${sampleAreaName(current.start.groupId,current.start.area)}${if(current.closed) "（終了）" else "（安否確認中）"} ▾"
        else if(state.selection=="legacy") "過去の情報（災害未分類・閲覧のみ） ▾" else "災害・訓練を選択 ▾", style=MaterialTheme.typography.labelLarge, maxLines=2, overflow=TextOverflow.Ellipsis)
    }
    if(expanded) AlertDialog(onDismissRequest={expanded=false},title={Text("表示する災害・訓練")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if(state.options.isEmpty())Text("安否確認はまだありません")
            state.options.forEach { option -> TextButton(onClick={model.selectIncident(option.start.eventId);expanded=false}) {
                Text("${if(option.start.training) "訓練" else "地震"}：${sampleIncidentTitle(option.start.groupId,option.start.title)}\n対象地域：${sampleAreaName(option.start.groupId,option.start.area)}\n開始した人：${publicRecordAuthor(option.start.authorId,state.residents,state.care)}\n開始日時：${formatTime(option.start.createdAt)}${if(option.closed) "（終了）" else ""}")
            } }
            TextButton(onClick={model.selectIncident("legacy");expanded=false}) { Text("過去の情報（災害未分類・閲覧のみ）") }
        }
    },confirmButton={TextButton(onClick={expanded=false}){Text("閉じる")}})
}

@Composable fun IncidentActions(model: PlanViewModel, registered: Boolean) {
    val state by model.incidentUi.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    var showStart by rememberSaveable { mutableStateOf(false) }
    var showEnd by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("地震の安否確認") }
    var area by rememberSaveable { mutableStateOf("") }
    var training by rememberSaveable { mutableStateOf(false) }
    var fictional by rememberSaveable { mutableStateOf(false) }
    val cloudState by model.cloud.state.collectAsStateWithLifecycle()
    val busy=saving||cloudState.busy
    val roster by model.roster.collectAsStateWithLifecycle()
    val canTrain=roster!=null&&model.canStartTraining()
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val current=state.current
        Text(when { current!=null -> if(current.closed) "安否確認は終了しました" else "安否確認中"
            state.selection=="legacy" -> "過去の情報"
            state.options.count { !it.closed }>1 -> "表示する安否確認を選択"
            else -> "安否確認は未開始" })
        Text(uiText("training_manage"),style=MaterialTheme.typography.titleLarge)
        if(canTrain) {
            if(state.options.any {it.start.training&&!it.closed}) Text(uiText("training_running"))
            Button(enabled=registered&&!saving&&state.options.none {it.start.training&&!it.closed},onClick={training=true;title="安否確認の訓練";showStart=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(uiText("training_start"))}
        } else Text(uiText(if(roster==null)"training_join" else "training_wait"))
        HorizontalDivider(Modifier.padding(vertical=12.dp))
        Text(uiText("earthquake_report_section"),style=MaterialTheme.typography.titleMedium)
        OutlinedButton(enabled=registered&&!saving,onClick={training=false;title="地震の安否確認";showStart=true},modifier=Modifier.fillMaxWidth()) { Text(uiText("report_earthquake")) }
        if(current!=null&&!current.closed) {
            if(model.canEndIncident(current.start.authorId)) OutlinedButton(enabled=!saving,onClick={showEnd=true}){Text("この安否確認を終了")}
        }
        OperationFeedback(model,"incident")
    }
    if(showStart) AlertDialog(onDismissRequest={if(!saving)showStart=false},title={Text(uiText(if(training)"training_start"else "actual_report_start"))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title,{title=it.take(80)},label={Text(uiText("incident_name"))},enabled=!saving)
            OutlinedTextField(area,{area=it.take(120)},label={Text(uiText("target_area"))},enabled=!saving)
            if(training){
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(fictional,{fictional=it;if(it&&area.isBlank())area="御所南〜烏丸御池"},enabled=!busy);Text("京都に架空の位置を割り当てる",Modifier.weight(1f))}
                if(fictional)Text("管理者を含む参加者を分散表示します。住居や現在地は変更しません。",style=MaterialTheme.typography.bodySmall)
                cloudState.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            }
            OperationFeedback(model,"incident")
        }
    },confirmButton={TextButton(enabled=!busy&&title.isNotBlank()&&area.isNotBlank()&&(!training||canTrain),onClick={if(training&&fictional)model.cloud.startVenue("",true,title,area){showStart=false}else model.startIncident(title,area,training)}) { Text(uiText(if(saving)"saving"else if(training)"training_start"else "actual_report_start")) }},dismissButton={TextButton(enabled=!saving,onClick={showStart=false}){Text(uiText("close"))}})
    val saveStates by model.saveStates.collectAsStateWithLifecycle()
    val initialSelection=remember(showStart){state.selection}
    LaunchedEffect(state.selection,showStart,saving) { if(showStart&&!saving&&state.selection!=initialSelection&&saveStates["incident"]?.phase==io.github.nevaseme.plandmk1.core.SavePhase.SAVED)showStart=false }
    if(showEnd) AlertDialog(onDismissRequest={showEnd=false},title={Text("この安否確認を終了しますか")},confirmButton={TextButton(onClick={model.endIncident();showEnd=false}){Text("終了する")}},dismissButton={TextButton(onClick={showEnd=false}){Text("戻る")}})
}
