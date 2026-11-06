package io.github.nevaseme.plandmk1.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.BuildConfig
import io.github.nevaseme.plandmk1.PlanViewModel

@Composable
fun CloudSharingPanel(model:PlanViewModel,ownName:String,onCommunication:()->Unit={},onIncidents:()->Unit={}) {
    val state by model.cloud.state.collectAsStateWithLifecycle()
    val permissionRoster by model.roster.collectAsStateWithLifecycle()
    var endpoint by rememberSaveable {mutableStateOf(BuildConfig.CLOUD_SYNC_URL)}
    var name by rememberSaveable(ownName){mutableStateOf(ownName)}
    var groupName by rememberSaveable {mutableStateOf("")}
    var code by rememberSaveable {mutableStateOf("")}
    var changing by rememberSaveable(state.groupId){mutableStateOf(false)}
    var page by rememberSaveable(state.groupId) {mutableStateOf("overview")}
    var help by rememberSaveable {mutableStateOf(false)}
    var entry by rememberSaveable {mutableStateOf("join")}
    var choosingCity by remember {mutableStateOf(false)}
    var confirmingLeave by remember {mutableStateOf(false)}
    val nameStep by model.cloud.venueNameStep.collectAsStateWithLifecycle()
    val entryPositionMode by model.cloud.venueEntryPositionMode.collectAsStateWithLifecycle()
    val password by model.cloud.venuePasswordDraft.collectAsStateWithLifecycle()
    val creationPassword by model.cloud.groupCreationPasswordDraft.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val live=state.venue?.closed==false
    BackHandler(page!="overview"||changing){if(changing)changing=false else page="overview"}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(state.connected&&!changing&&page=="overview") {
            SectionTitle(state.groupName.ifBlank {menuText("グループ")})
            Text(menuText(if(live)"${if(state.venue?.presentationMode=="EARTHQUAKE")"地震" else "訓練"}開催中 · ${state.venue?.placements?.size?:0}人" else if(state.admin)"訓練を準備しましょう" else "管理者の訓練開始を待っています"))
            if(live) {
                Text(menuText(if(state.venue?.positionMode=="ACTUAL")"参加者の現在地を表示" else "京都の割当位置を表示"),style=MaterialTheme.typography.bodyMedium)
                if(state.admin)Text(menuText("参加する人に、このグループ名と設定した参加パスワードを伝えてください。"),style=MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick=onIncidents){Text(uiText("training_incidents"))}
            HorizontalDivider()
            TextButton(onClick={page="settings"},modifier=Modifier.fillMaxWidth()){Text(menuText("グループの設定"))}
        }
        if(state.connected&&!changing&&page!="overview") {
            TextButton(onClick={page="overview"}){Text(menuText("グループに戻る"))}
            if(page=="recovery") DemoAccessPanel(model) else {
                SectionTitle(menuText("グループの設定"))
                if(state.admin){
                    Text(if(LocalUiLanguage.current=="en")"Injury and urgency permissions" else "負傷登録・緊急度設定の権限",style=MaterialTheme.typography.titleMedium)
                    if(permissionRoster?.members?.none {it.role!=io.github.nevaseme.plandmk1.core.MemberRole.ADMIN}!=false)Text(if(LocalUiLanguage.current=="en")"No participants to assign permissions to yet." else "権限を設定する参加者はまだいません。")
                    permissionRoster?.members?.filter {it.role!=io.github.nevaseme.plandmk1.core.MemberRole.ADMIN}?.forEach {person->
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(person.activityName,Modifier.weight(1f));Switch(person.canTriage,{model.cloud.setMemberPermission(person.memberId,it)},enabled=!state.busy)}
                    }
                }
                Text(menuText("地図保存・住所検索の地域"),style=MaterialTheme.typography.titleMedium)
                Text(menuText(state.municipality?.let{"${it.prefecture} ${it.name}"}?:"未設定"))
                Text(if(LocalUiLanguage.current=="en")"Used for offline maps and address searches. This does not change neighborhood boundaries." else "地図保存と住所検索に使う市です。町内会の範囲は変わりません。",style=MaterialTheme.typography.bodySmall)
                if(state.admin) {
                    TextButton(onClick={choosingCity=!choosingCity},enabled=!state.busy){Text(menuText(if(choosingCity)"選択を閉じる" else "市を変更"))}
                    if(choosingCity)io.github.nevaseme.plandmk1.cloud.GroupMunicipality.available.forEach {city->
                        TextButton(onClick={model.cloud.setMunicipality(city.code);choosingCity=false},enabled=!state.busy){Text(menuText("${city.prefecture} ${city.name}"))}
                    }
                }
                HorizontalDivider()
                if(live&&state.venue?.recoveryAvailable==true)TextButton(onClick={page="recovery"}){Text(menuText("参加の復元・管理"))}
                TextButton(onClick=onCommunication){Text(menuText("通信の詳細"))}
                TextButton(onClick={changing=true},enabled=!state.busy){Text(menuText("別のグループに参加"))}
                if(!state.admin)TextButton(onClick={confirmingLeave=true},enabled=!state.busy){Text(menuText("グループを退会"))}
                if(state.admin) {
                    TextButton(onClick={page="invite"},enabled=!state.busy&&page!="invite"){Text(menuText("招待コードを使う"))}
                    if(page=="invite") {
                        Text(menuText("会場訓練はグループ名とパスワードで参加できます。招待コードは別の参加方法です。"),style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick=model.cloud::invite,enabled=!state.busy){Text(menuText("招待コードを発行"))}
                        state.invitation?.let {value->TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Help Net グループ招待",value));model.message.value="招待コードをコピーしました"}){Text(menuText("招待コードをコピー（24時間有効）"))}}
                    }
                }
            }
        }
        if(!state.connected||changing) {
            if(changing)TextButton(onClick={changing=false}){Text(menuText("グループの設定に戻る"))}
            SectionTitle(menuText(when(entry){"create"->"グループを作成";"recover"->"以前の参加を復元";"invite"->"招待コードで参加";else->if(nameStep)"あなたの名前" else "グループに参加"}))
            if(endpoint.isBlank()||BuildConfig.CLOUD_SYNC_URL.isBlank())OutlinedTextField(endpoint,{endpoint=it},label={Text(menuText("共有サーバーのアドレス"))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
            when(entry) {
                "join"->if(!nameStep){
                    Text(menuText("管理者から案内された参加情報を入力してください。"),style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(groupName,{groupName=it},label={Text(menuText("グループ名"))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(password,{model.cloud.venuePasswordDraft.value=it},label={Text(menuText("参加パスワード"))},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    Button(onClick={model.cloud.checkVenue(endpoint,groupName,password){model.cloud.venueNameStep.value=true}},enabled=!state.busy&&groupName.isNotBlank()&&password.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(menuText("次へ"))}
                }else{
                    Text(groupName)
                    Text(menuText(if(entryPositionMode=="ACTUAL")"位置情報を許可すると、現在地をグループに共有します。" else "京都の割当位置で参加します。"),style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(name,{name=it},label={Text(menuText("あなたの名前"))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    if(entry=="create")OutlinedTextField(creationPassword,{model.cloud.groupCreationPasswordDraft.value=it},label={Text(menuText("参加パスワード"))},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    Button(onClick={model.cloud.joinVenue(endpoint,groupName,password,name)},enabled=!state.busy&&name.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(menuText("参加して安否を回答"))}
                    TextButton(onClick={model.cloud.venueNameStep.value=false}){Text(menuText("参加情報に戻る"))}
                }
                "recover"->{
                    Text(menuText("自分用の復元コードを入力してください。控え忘れた場合は管理者に発行を依頼できます。"),style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(groupName,{groupName=it},label={Text(menuText("グループ名"))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(code,{code=it},label={Text(menuText("復元コード"))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    Button(onClick={model.cloud.recover(endpoint,groupName,code)},enabled=!state.busy&&groupName.isNotBlank()&&code.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(menuText("参加を復元"))}
                }
                else->{
                    if(entry=="create")OutlinedTextField(groupName,{groupName=it},label={Text(menuText("グループ名"))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    else OutlinedTextField(code,{code=it},label={Text(menuText("招待コード"))},enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(name,{name=it},label={Text(menuText("あなたの名前"))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    if(entry=="create")OutlinedTextField(creationPassword,{model.cloud.groupCreationPasswordDraft.value=it},label={Text(menuText("参加パスワード"))},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    Button(onClick={if(entry=="create")model.cloud.create(endpoint,name,groupName,creationPassword) else model.cloud.join(endpoint,name,code)},enabled=!state.busy&&name.isNotBlank()&&(if(entry=="create")groupName.isNotBlank()&&creationPassword.isNotBlank()&&creationPassword.length<=128 else code.isNotBlank()),modifier=Modifier.fillMaxWidth()){Text(menuText(if(entry=="create")"作成して訓練を準備" else "参加する"))}
                }
            }
            if(entry!="join")TextButton(onClick={entry="join";help=false},enabled=!state.busy){Text(menuText("グループ名・パスワードで参加"))}
            else {
                TextButton(onClick={help=!help}){Text(menuText("参加できないとき"))}
                if(help){TextButton(onClick={entry="recover";help=false}){Text(menuText("以前の参加を復元する"))};TextButton(onClick={entry="invite";help=false}){Text(menuText("招待コードで参加する"))}}
                TextButton(onClick={entry="create";help=false},enabled=!state.busy){Text(menuText("管理者としてグループを作成"))}
            }
            if(state.pendingRegistration)TextButton(onClick=model.cloud::retryRegistration,enabled=!state.busy){Text(menuText("前回の操作を再試行"))}
            if(state.busy)StatusFeedback(menuText(state.status),busy=true)
        }
        state.error?.let {InfoCard(menuText(it),true)}
    }
    if(confirmingLeave)AlertDialog(onDismissRequest={confirmingLeave=false},title={Text(menuText("グループを退会"))},text={Text(menuText("共有を停止し、過去の記録は残します。グループ名とパスワードで再参加できます。"))},confirmButton={TextButton(onClick={confirmingLeave=false;model.cloud.leaveGroup()}){Text(menuText("退会する"))}},dismissButton={TextButton(onClick={confirmingLeave=false}){Text(menuText("キャンセル"))}})
}

@Composable private fun DemoAccessPanel(model:PlanViewModel) {
    val state by model.cloud.state.collectAsStateWithLifecycle()
    val roster by model.roster.collectAsStateWithLifecycle()
    val code by model.cloud.recoveryCode.collectAsStateWithLifecycle()
    val members=roster?.members.orEmpty().filter {it.role==io.github.nevaseme.plandmk1.core.MemberRole.MEMBER}
    var choosing by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf<String?>(null)}
    var confirming by remember {mutableStateOf(false)}
    val member=members.find {it.memberId==selected}?:members.firstOrNull()
    val context=LocalContext.current
    SectionTitle(menuText("参加の復元・管理"))
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(menuText("復元コードは24時間以内に1回使えます。復元すると古い端末の接続は停止します。"),style=MaterialTheme.typography.bodySmall)
        if(state.admin)TextButton(onClick={choosing=true},enabled=members.isNotEmpty()&&!state.busy){Text(menuText(member?.activityName?:"対象の参加者はいません"))}
        OutlinedButton(onClick={model.cloud.manageAccess(if(state.admin)member?.memberId else null)},enabled=!state.busy&&(!state.admin||member!=null)){Text(menuText("復元コードを発行"))}
        code?.let {value->
            Text(value,style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("本人用の復元コード",value));model.message.value="復元コードをコピーしました"}){Text(menuText("復元コードをコピー"))}
        }
        if(state.admin)TextButton(onClick={confirming=true},enabled=!state.busy&&member!=null){Text(menuText("この参加登録を停止"))}
    }
    if(choosing)AlertDialog(onDismissRequest={choosing=false},title={Text(menuText("参加者を選ぶ"))},text={androidx.compose.foundation.lazy.LazyColumn {items(members.size){index->val item=members[index];TextButton(onClick={selected=item.memberId;choosing=false;model.cloud.recoveryCode.value=null}){Text(menuText(item.activityName+if(item.memberId in state.venue?.inactiveMemberIds.orEmpty())"（停止中）" else ""))}}}},confirmButton={},dismissButton={TextButton(onClick={choosing=false}){Text(menuText("閉じる"))}})
    if(confirming)AlertDialog(onDismissRequest={confirming=false},title={Text(menuText("参加登録を停止"))},text={Text(menuText("${member?.activityName}の接続を停止し、会場の地図から外します。過去の記録は残ります。"))},confirmButton={TextButton(onClick={confirming=false;model.cloud.manageAccess(member?.memberId,true)}){Text(menuText("停止する"))}},dismissButton={TextButton(onClick={confirming=false}){Text(menuText("キャンセル"))}})
}

@Composable
fun CloudCommunicationDetails(model: PlanViewModel) {
    val state by model.cloud.state.collectAsStateWithLifecycle()
    val synchronizing by model.cloud.synchronization.collectAsStateWithLifecycle()
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle(menuText("インターネットで共有"))
        StatusFeedback(menuText(if(synchronizing) "同期しています…" else state.status),busy=state.busy||synchronizing)
        if(state.connected) CloudSyncButton(state.busy||synchronizing, model.cloud::sync)
        state.syncError?.let {InfoCard(menuText(it),true)}
        if(state.unsentForeign>0) InfoCard(menuText("近くの端末から受け取った${state.unsentForeign}件は、この端末に保存済みです。記録した本人の端末が接続するとインターネットで共有されます。"))
        if(state.rejected>0) InfoCard(menuText("${state.rejected}件を送信できませんでした。記録は端末に保存されています。"),true)
    }
}

@Composable
fun CloudSyncButton(busy: Boolean, onSync: () -> Unit) {
    // Periodic sync is signalled by the status card. Keep the action visible while ignoring duplicates.
    Button(onClick=onSync,enabled=!busy,modifier=Modifier.fillMaxWidth(),
        colors=ButtonDefaults.buttonColors(
            disabledContainerColor=MaterialTheme.colorScheme.primary,
            disabledContentColor=MaterialTheme.colorScheme.onPrimary
        )){Text(menuText("今すぐ同期"))}
}

/** The administrator prepares, starts, and ends the drill in the group screen. */
@Composable fun CloudVenueTraining(model:PlanViewModel) {
    val state by model.cloud.state.collectAsStateWithLifecycle()
    val positionMode by model.cloud.venuePositionModeDraft.collectAsStateWithLifecycle()
    var confirming by remember {mutableStateOf(false)}
    var initialMinutes by rememberSaveable {mutableStateOf("3")}
    var presentationMode by rememberSaveable {mutableStateOf("DRILL")}
    if(state.connected&&state.admin)Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(state.venue?.closed==false) {
            val venue=state.venue!!;val english=LocalUiLanguage.current=="en"
            var minutes by remember(venue.sessionId,venue.simulationStartedAt){mutableStateOf(((System.currentTimeMillis()-(venue.simulationStartedAt?:venue.startedAt))/60000).coerceAtLeast(0).toString())}
            OutlinedTextField(minutes,{minutes=it},singleLine=true,label={Text(menuText(if(english)"Minutes since the earthquake" else "地震発生からの経過分数"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),modifier=Modifier.fillMaxWidth())
            TextButton(enabled=!state.busy&&minutes.toIntOrNull() in 0..1440,onClick={minutes.toIntOrNull()?.let {model.cloud.setVenueClock(it)}}){Text(menuText(if(english)"Apply time" else "経過時間を変更"))}
            TextButton(onClick={confirming=true},enabled=!state.busy){Text(if(venue.presentationMode=="EARTHQUAKE"){if(english)"End earthquake" else "地震を終了する"}else menuText("訓練を終了する"))}
        }
        else VenueSetupForm(state.busy,initialMinutes,{initialMinutes=it},presentationMode,{presentationMode=it}){mode->model.cloud.startVenue("",positionMode="KYOTO",elapsedMinutes=initialMinutes.toInt(),presentationMode=presentationMode)}
    }
    if(confirming)AlertDialog(onDismissRequest={confirming=false},title={Text(if(state.venue?.presentationMode=="EARTHQUAKE"){if(LocalUiLanguage.current=="en")"End this earthquake?" else "地震を終了しますか？"}else menuText("訓練を終了しますか？"))},text={Text(menuText("記録は残ります。次回は新しい安否回答から始められます。"))},confirmButton={TextButton(onClick={confirming=false;model.cloud.endVenue()}){Text(menuText("終了する"))}},dismissButton={TextButton(onClick={confirming=false}){Text(menuText("キャンセル"))}})
}

/** Invalid input explains how to proceed; only an in-flight operation disables submission. */
@Composable fun VenueSetupForm(busy:Boolean,initialMinutes:String="3",onInitialMinutes:(String)->Unit={},presentationMode:String="DRILL",onPresentationMode:(String)->Unit={},onStart:(String)->Unit) {
    val english=LocalUiLanguage.current=="en"
    fun tr(ja:String,en:String)=if(english)en else ja
    var attempted by rememberSaveable {mutableStateOf(false)}
    val keyboard=LocalSoftwareKeyboardController.current
    val invalidMinutes=initialMinutes.toIntOrNull() !in 0..1440
    fun submit(){if(!busy){attempted=true;if(!invalidMinutes){keyboard?.hide();onStart("KYOTO")}}}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(initialMinutes,onInitialMinutes,singleLine=true,label={Text(menuText(tr("開始時の経過分数","Elapsed minutes at start")))},isError=attempted&&invalidMinutes,supportingText={if(attempted&&invalidMinutes)Text(menuText(tr("0〜1440分の整数で入力してください","Enter 0–1440 whole minutes")))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Next),modifier=Modifier.fillMaxWidth())
        Text(tr("モード","Mode"),style=MaterialTheme.typography.titleSmall)
        listOf("DRILL" to tr("訓練","Drill"),"EARTHQUAKE" to tr("地震","Earthquake")).forEach {(value,label)->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=presentationMode==value,enabled=!busy,role=Role.RadioButton,onClick={onPresentationMode(value)}),verticalAlignment=Alignment.CenterVertically){RadioButton(selected=presentationMode==value,onClick=null,enabled=!busy);Spacer(Modifier.width(8.dp));Text(label)}
        }
        Button(onClick=::submit,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(menuText(if(busy)tr("開始しています…","Starting…") else if(presentationMode=="EARTHQUAKE")tr("地震を開始する","Start earthquake")else tr("訓練を開始する","Start drill")))}
    }
}
