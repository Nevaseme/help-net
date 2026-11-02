package io.github.nevaseme.plandmk1.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.BuildConfig
import io.github.nevaseme.plandmk1.PlanViewModel

@Composable
fun CloudSharingPanel(model:PlanViewModel,ownName:String) {
    val state by model.cloud.state.collectAsStateWithLifecycle()
    var endpoint by rememberSaveable {mutableStateOf(BuildConfig.CLOUD_SYNC_URL)}
    var name by rememberSaveable(ownName){mutableStateOf(ownName)}
    var groupName by rememberSaveable {mutableStateOf("")}
    var code by rememberSaveable {mutableStateOf("")}
    var changing by rememberSaveable(state.groupId){mutableStateOf(false)}
    var groupSettings by rememberSaveable {mutableStateOf(false)}
    var help by rememberSaveable {mutableStateOf(false)}
    var entry by rememberSaveable {mutableStateOf("join")}
    var choosingCity by remember {mutableStateOf(false)}
    val nameStep by model.cloud.venueNameStep.collectAsStateWithLifecycle()
    val entryPositionMode by model.cloud.venueEntryPositionMode.collectAsStateWithLifecycle()
    val password by model.cloud.venuePasswordDraft.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val live=state.venue?.closed==false
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(state.connected) {
            SectionTitle(state.groupName.ifBlank {"グループ"})
            Text(if(live)"デモ開催中 · ${state.venue?.placements?.size?:0}人" else if(state.admin)"デモを準備しましょう" else "管理者のデモ開始を待っています")
            if(live) {
                Text(if(state.venue?.positionMode=="ACTUAL")"参加者の現在地を表示" else "京都の架空位置を表示",style=MaterialTheme.typography.bodyMedium)
                Button(onClick={model.openSafety.value=true},modifier=Modifier.fillMaxWidth()){Text("安否を回答する")}
                if(state.admin)Text("参加する人に、このグループ名と設定した参加パスワードを伝えてください。",style=MaterialTheme.typography.bodySmall)
            }
            if(state.admin)CloudVenueTraining(model)
            if(live&&state.venue?.recoveryAvailable==true)DemoAccessPanel(model)
            TextButton(onClick={groupSettings=!groupSettings}){Text(if(groupSettings)"グループの設定を閉じる" else "グループの設定")}
            if(groupSettings) {
                Text("活動地域：${state.municipality?.name?:"未設定"}")
                if(state.admin)TextButton(onClick={choosingCity=true},enabled=!state.busy){Text("活動地域を設定")}
                if(state.admin)TextButton(onClick=model.cloud::invite,enabled=!state.busy){Text("招待コードを発行")}
                state.invitation?.let {value->TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PlanD グループ招待",value));model.message.value="招待コードをコピーしました"}){Text("招待コードをコピー（24時間有効）")}}
                TextButton(onClick={changing=!changing},enabled=!state.busy){Text(if(changing)"入力を閉じる" else "別のグループに参加")}
            }
        }
        if(!state.connected||changing) {
            SectionTitle(when(entry){"create"->"グループを作成";"recover"->"以前の参加を復元";"invite"->"招待コードで参加";else->if(nameStep)"あなたの名前" else "グループに参加"})
            if(endpoint.isBlank()||BuildConfig.CLOUD_SYNC_URL.isBlank())OutlinedTextField(endpoint,{endpoint=it},label={Text("共有サーバーのアドレス")},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
            when(entry) {
                "join"->if(!nameStep){
                    Text("管理者から案内された参加情報を入力してください。",style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(groupName,{groupName=it},label={Text("グループ名")},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(password,{model.cloud.venuePasswordDraft.value=it},label={Text("参加パスワード")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    Button(onClick={model.cloud.checkVenue(endpoint,groupName,password){model.cloud.venueNameStep.value=true}},enabled=!state.busy&&groupName.isNotBlank()&&password.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("次へ")}
                }else{
                    Text(groupName)
                    Text(if(entryPositionMode=="ACTUAL")"位置情報を許可すると、現在地をグループに共有します。" else "京都の架空位置で参加します。",style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(name,{name=it},label={Text("あなたの名前")},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    Button(onClick={model.cloud.joinVenue(endpoint,groupName,password,name)},enabled=!state.busy&&name.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("参加して安否を回答")}
                    TextButton(onClick={model.cloud.venueNameStep.value=false}){Text("参加情報に戻る")}
                }
                "recover"->{
                    Text("自分用の復元コードを入力してください。控え忘れた場合は管理者に発行を依頼できます。",style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(groupName,{groupName=it},label={Text("グループ名")},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(code,{code=it},label={Text("復元コード")},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    Button(onClick={model.cloud.recover(endpoint,groupName,code)},enabled=!state.busy&&groupName.isNotBlank()&&code.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("参加を復元")}
                }
                else->{
                    if(entry=="create")OutlinedTextField(groupName,{groupName=it},label={Text("グループ名")},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    else OutlinedTextField(code,{code=it},label={Text("招待コード")},enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(name,{name=it},label={Text("あなたの名前")},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                    Button(onClick={if(entry=="create")model.cloud.create(endpoint,name,groupName) else model.cloud.join(endpoint,name,code)},enabled=!state.busy&&name.isNotBlank()&&(if(entry=="create")groupName.isNotBlank() else code.isNotBlank()),modifier=Modifier.fillMaxWidth()){Text(if(entry=="create")"作成してデモを準備" else "参加する")}
                }
            }
            if(entry!="join")TextButton(onClick={entry="join";help=false},enabled=!state.busy){Text("グループ名・パスワードで参加")}
            else {
                TextButton(onClick={help=!help}){Text("参加できないとき")}
                if(help){TextButton(onClick={entry="recover";help=false}){Text("以前の参加を復元する")};TextButton(onClick={entry="invite";help=false}){Text("招待コードで参加する")}}
                TextButton(onClick={entry="create";help=false},enabled=!state.busy){Text("管理者としてグループを作成")}
            }
            if(state.pendingRegistration)TextButton(onClick=model.cloud::retryRegistration,enabled=!state.busy){Text("前回の操作を再試行")}
            if(state.busy)StatusFeedback(state.status,busy=true)
        }
        state.error?.let {InfoCard(it,true)}
    }
    if(choosingCity)AlertDialog(onDismissRequest={choosingCity=false},title={Text("活動地域の市")},text={Column {io.github.nevaseme.plandmk1.cloud.GroupMunicipality.available.forEach {city->TextButton(onClick={model.cloud.setMunicipality(city.code);choosingCity=false}){Text("${city.prefecture} ${city.name}")}}}},confirmButton={},dismissButton={TextButton(onClick={choosingCity=false}){Text("キャンセル")}})
}

@Composable private fun DemoAccessPanel(model:PlanViewModel) {
    val state by model.cloud.state.collectAsStateWithLifecycle()
    val roster by model.roster.collectAsStateWithLifecycle()
    val code by model.cloud.recoveryCode.collectAsStateWithLifecycle()
    val members=roster?.members.orEmpty().filter {it.role==io.github.nevaseme.plandmk1.core.MemberRole.MEMBER}
    var expanded by remember {mutableStateOf(false)}
    var choosing by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf<String?>(null)}
    var confirming by remember {mutableStateOf(false)}
    val member=members.find {it.memberId==selected}?:members.firstOrNull()
    val context=LocalContext.current
    TextButton(onClick={expanded=!expanded}){Text("参加の復元・管理")}
    if(expanded)Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("復元コードは24時間以内に1回使えます。復元すると古い端末の接続は停止します。",style=MaterialTheme.typography.bodySmall)
        if(state.admin)TextButton(onClick={choosing=true},enabled=members.isNotEmpty()&&!state.busy){Text(member?.activityName?:"対象の参加者はいません")}
        OutlinedButton(onClick={model.cloud.manageAccess(if(state.admin)member?.memberId else null)},enabled=!state.busy&&(!state.admin||member!=null)){Text("復元コードを発行")}
        code?.let {value->
            Text(value,style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("本人用の復元コード",value));model.message.value="復元コードをコピーしました"}){Text("復元コードをコピー")}
        }
        if(state.admin)TextButton(onClick={confirming=true},enabled=!state.busy&&member!=null){Text("この参加登録を停止")}
    }
    if(choosing)AlertDialog(onDismissRequest={choosing=false},title={Text("参加者を選ぶ")},text={androidx.compose.foundation.lazy.LazyColumn {items(members.size){index->val item=members[index];TextButton(onClick={selected=item.memberId;choosing=false;model.cloud.recoveryCode.value=null}){Text(item.activityName+if(item.memberId in state.venue?.inactiveMemberIds.orEmpty())"（停止中）" else "")}}}},confirmButton={},dismissButton={TextButton(onClick={choosing=false}){Text("閉じる")}})
    if(confirming)AlertDialog(onDismissRequest={confirming=false},title={Text("参加登録を停止")},text={Text("${member?.activityName}の接続を停止し、会場の地図から外します。過去の記録は残ります。")},confirmButton={TextButton(onClick={confirming=false;model.cloud.manageAccess(member?.memberId,true)}){Text("停止する")}},dismissButton={TextButton(onClick={confirming=false}){Text("キャンセル")}})
}

@Composable
fun CloudCommunicationDetails(model: PlanViewModel) {
    val state by model.cloud.state.collectAsStateWithLifecycle()
    val synchronizing by model.cloud.synchronization.collectAsStateWithLifecycle()
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle("インターネットで共有")
        StatusFeedback(if(synchronizing) "同期しています…" else state.status,busy=state.busy||synchronizing)
        if(state.connected) CloudSyncButton(state.busy||synchronizing, model.cloud::sync)
        state.syncError?.let {InfoCard(it,true)}
        if(state.unsentForeign>0) InfoCard("近くの端末から受け取った${state.unsentForeign}件は、この端末に保存済みです。記録した本人の端末が接続するとインターネットで共有されます。")
        if(state.rejected>0) InfoCard("${state.rejected}件を送信できませんでした。記録は端末に保存されています。",true)
    }
}

@Composable
fun CloudSyncButton(busy: Boolean, onSync: () -> Unit) {
    // Periodic sync is signalled by the status card. Keep the action visible while ignoring duplicates.
    Button(onClick=onSync,enabled=!busy,modifier=Modifier.fillMaxWidth(),
        colors=ButtonDefaults.buttonColors(
            disabledContainerColor=MaterialTheme.colorScheme.primary,
            disabledContentColor=MaterialTheme.colorScheme.onPrimary
        )){Text("今すぐ同期")}
}

/** The administrator prepares, starts, and ends the demo in the group screen. */
@Composable fun CloudVenueTraining(model:PlanViewModel) {
    val state by model.cloud.state.collectAsStateWithLifecycle()
    val adminPassword by model.cloud.venueAdminPasswordDraft.collectAsStateWithLifecycle()
    var actualPosition by rememberSaveable {mutableStateOf(false)}
    var confirming by remember {mutableStateOf(false)}
    if(state.connected&&state.admin)Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(state.venue?.closed==false)TextButton(onClick={confirming=true},enabled=!state.busy){Text("デモを終了する")}
        else {
            Text("チーム内の練習も、本番も同じ操作で始められます。",style=MaterialTheme.typography.bodyMedium)
            Text("地図上の位置",style=MaterialTheme.typography.titleSmall)
            TextButton(onClick={actualPosition=false},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){RadioButton(selected=!actualPosition,onClick=null);Text("京都の架空位置",modifier=Modifier.weight(1f))}
            TextButton(onClick={actualPosition=true},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){RadioButton(selected=actualPosition,onClick=null);Text("参加者の現在地",modifier=Modifier.weight(1f))}
            Text(if(actualPosition)"各端末で位置情報を許可してください。取得できない人は位置不明になります。" else "御所南〜烏丸御池に分散して表示します。",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(adminPassword,{model.cloud.venueAdminPasswordDraft.value=it},label={Text("参加パスワード")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
            Button(onClick={model.cloud.startVenue(adminPassword,positionMode=if(actualPosition)"ACTUAL" else "KYOTO")},enabled=!state.busy&&adminPassword.length in 8..128,modifier=Modifier.fillMaxWidth()){Text("デモを開始する")}
        }
    }
    if(confirming)AlertDialog(onDismissRequest={confirming=false},title={Text("デモを終了しますか？")},text={Text("記録は残ります。次回は新しい安否回答から始められます。")},confirmButton={TextButton(onClick={confirming=false;model.cloud.endVenue()}){Text("終了する")}},dismissButton={TextButton(onClick={confirming=false}){Text("キャンセル")}})
}
