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
import io.github.nevaseme.plandmk1.cloud.mapMunicipalityOrNull

@Composable fun IncidentSelector(model: PlanViewModel) {
    val state by model.incidentUi.collectAsStateWithLifecycle()
    val roster by model.roster.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val current=state.current
    val cloud by model.cloud.state.collectAsStateWithLifecycle()
    fun drill(start:io.github.nevaseme.plandmk1.core.IncidentEvent)=cloud.venue?.displaysAsDrill(start)?:start.training
    val english=LocalUiLanguage.current=="en"
    TextButton(enabled=!saving,onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {
        Text(if(english&&current!=null) "${if(drill(current.start)) "Drill" else "Earthquake"}: ${menuText(sampleIncidentTitle(current.start.groupId,current.start.title))} / ${menuText(sampleAreaName(current.start.groupId,current.start.area))} · ${if(current.closed) "Ended" else "Active"} ▾" else menuText(if(current!=null) "表示中の${if(drill(current.start)) "訓練" else "地震"}：${menuText(sampleIncidentTitle(current.start.groupId,current.start.title))}／対象地域：${menuText(sampleAreaName(current.start.groupId,current.start.area))}${if(current.closed) "（終了）" else "（安否確認中）"} ▾"
        else if(state.selection=="legacy") "過去の情報（災害未分類・閲覧のみ） ▾" else "災害・訓練を選択 ▾"), style=MaterialTheme.typography.labelLarge, maxLines=2, overflow=TextOverflow.Ellipsis)
    }
    if(expanded) AlertDialog(onDismissRequest={expanded=false},title={Text(menuText("表示する災害・訓練"))},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if(state.options.isEmpty())Text(menuText("安否確認はまだありません"))
            state.options.forEach { option -> TextButton(onClick={model.selectIncident(option.start.eventId);expanded=false}) {
                Text(if(english)"${if(drill(option.start)) "Drill" else "Earthquake"}: ${menuText(sampleIncidentTitle(option.start.groupId,option.start.title))}\nArea: ${menuText(sampleAreaName(option.start.groupId,option.start.area))}\nStarted by: ${publicRecordAuthor(option.start.authorId,state.residents,state.care,roster)}\nStarted: ${formatTime(option.start.createdAt)}${if(option.closed) " (Ended)" else ""}" else menuText("${if(drill(option.start)) "訓練" else "地震"}：${menuText(sampleIncidentTitle(option.start.groupId,option.start.title))}\n対象地域：${menuText(sampleAreaName(option.start.groupId,option.start.area))}\n開始した人：${publicRecordAuthor(option.start.authorId,state.residents,state.care,roster)}\n開始日時：${formatTime(option.start.createdAt)}${if(option.closed) "（終了）" else ""}"))
            } }
            TextButton(onClick={model.selectIncident("legacy");expanded=false}) { Text(menuText("過去の情報（災害未分類・閲覧のみ）")) }
        }
    },confirmButton={TextButton(onClick={expanded=false}){Text(menuText("閉じる"))}})
}

@Composable fun IncidentActions(model: PlanViewModel, registered: Boolean, administrator:Boolean=false) {
    val state by model.incidentUi.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    var showStart by rememberSaveable { mutableStateOf(false) }
    var showEnd by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("地震の安否確認") }
    var training by rememberSaveable { mutableStateOf(false) }
    var fictional by rememberSaveable { mutableStateOf(false) }
    val cloudState by model.cloud.state.collectAsStateWithLifecycle()
    val english=LocalUiLanguage.current=="en"
    val area="京都市"
    val busy=saving||cloudState.busy
    val roster by model.roster.collectAsStateWithLifecycle()
    val canTrain=roster!=null&&model.canStartTraining()
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val current=state.current
        Text(menuText(when { current!=null -> if(current.closed) "安否確認は終了しました" else "安否確認中"
            state.selection=="legacy" -> "過去の情報"
            state.options.count { !it.closed }>1 -> "表示する安否確認を選択"
            else -> "安否確認は未開始" }))
        Text(uiText("training_manage"),style=MaterialTheme.typography.titleLarge)
        if(canTrain) {
            if(state.options.any {it.start.training&&!it.closed}) Text(uiText("training_running"))
            Button(enabled=(registered||roster!=null)&&!saving&&state.options.none {it.start.training&&!it.closed},onClick={training=true;title=if(english)"Safety drill" else "安否確認の訓練";showStart=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(uiText("training_start"))}
        } else Text(uiText(if(roster==null)"training_join" else "training_wait"))
        HorizontalDivider(Modifier.padding(vertical=12.dp))
        Text(uiText("earthquake_report_section"),style=MaterialTheme.typography.titleMedium)
        OutlinedButton(enabled=canTrain&&!saving&&state.options.none {it.start.training&&!it.closed},onClick={training=false;title=if(english)"Earthquake safety check" else "地震の安否確認";showStart=true},modifier=Modifier.fillMaxWidth()) { Text(if(administrator)menuText("地震を開始")else uiText("report_earthquake")) }
        if(current!=null&&!current.closed) {
            if(model.canEndIncident(current.start.authorId)) OutlinedButton(enabled=!saving,onClick={showEnd=true}){Text(menuText("この安否確認を終了"))}
        }
        OperationFeedback(model,"incident")
    }
    if(showStart) AlertDialog(onDismissRequest={if(!saving)showStart=false},title={Text(if(administrator&&!training)menuText("地震を開始")else uiText(if(training)"training_start"else "actual_report_start"))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title,{title=it.take(80)},label={Text(uiText("incident_name"))},enabled=!saving)
            cloudState.error?.let {Text(menuText(it),color=MaterialTheme.colorScheme.error)}
            OperationFeedback(model,"incident")
        }
    },confirmButton={TextButton(enabled=!busy&&title.isNotBlank()&&area.isNotBlank()&&canTrain,onClick={model.cloud.startVenue("",true,title,area,presentationMode=if(training)"DRILL" else "EARTHQUAKE"){showStart=false}}) { Text(if(administrator&&!training&&!saving)menuText("地震を開始")else uiText(if(saving)"saving"else if(training)"training_start"else "actual_report_start")) }},dismissButton={TextButton(enabled=!saving,onClick={showStart=false}){Text(uiText("close"))}})
    val saveStates by model.saveStates.collectAsStateWithLifecycle()
    val initialSelection=remember(showStart){state.selection}
    LaunchedEffect(state.selection,showStart,saving) { if(showStart&&!saving&&state.selection!=initialSelection&&saveStates["incident"]?.phase==io.github.nevaseme.plandmk1.core.SavePhase.SAVED)showStart=false }
    if(showEnd) AlertDialog(onDismissRequest={showEnd=false},title={Text(menuText("この安否確認を終了しますか"))},confirmButton={TextButton(onClick={model.endIncident();showEnd=false}){Text(menuText("終了する"))}},dismissButton={TextButton(onClick={showEnd=false}){Text(menuText("戻る"))}})
}
