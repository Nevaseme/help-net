package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.PlanViewModel
import io.github.nevaseme.plandmk1.core.*
import java.text.DateFormat
import java.util.Date

fun CareAction.label() = when (this) {
    CareAction.CHECKING -> "確認中"
    CareAction.NEEDS_HELP -> "要救助"
    CareAction.RESCUED -> "本人の救助完了"
    CareAction.REVOKED -> "記録を撤回"
}
fun careSummary(events: List<CareEvent>, subject: String,roster:MemberRoster?=null): String {
    val current = currentCareEvents(events.filter { it.subjectId == subject },roster)
    return if (current.isEmpty()) "対応記録なし" else current.joinToString(" / ") { it.action.label() }
}

@Composable
fun CareHistoryAndEditor(model: PlanViewModel, resident: Resident, events: List<CareEvent>, canEdit: Boolean, saving: Boolean) {
    val residents by model.residents.collectAsStateWithLifecycle()
    val roster by model.roster.collectAsStateWithLifecycle()
    var action by rememberSaveable(resident.originId) { mutableStateOf(CareAction.CHECKING) }
    var note by rememberSaveable(resident.originId) { mutableStateOf("") }
    var supersedes by rememberSaveable(resident.originId) { mutableStateOf<String?>(null) }
    val history = events.filter { it.subjectId == resident.originId }.sortedWith(compareByDescending<CareEvent> { it.createdAt }.thenBy { it.eventId })
    val current = currentCareEvents(history,roster).map { it.eventId }.toSet()
    HorizontalDivider()
    Text(menuText("この人への対応記録"), style = MaterialTheme.typography.titleMedium)
    if (history.isEmpty()) Text(menuText("まだ対応記録はありません"))
    history.forEach { event ->
        Text(menuText(event.action.label() + if (event.eventId !in current) "（訂正前）" else ""), style = MaterialTheme.typography.titleSmall)
        Text(menuText("記録した人：${publicRecordAuthor(event.authorId,residents,events,roster)}\n記録日時：${io.github.nevaseme.plandmk1.core.usDateTime(event.createdAt)}"))
        Text(menuText(event.note.ifBlank { "詳細なし" }))
        if (event.supersedes != null) Text(menuText("訂正記録"), style = MaterialTheme.typography.labelSmall)
        if (canEdit && event.authorId == model.localId && event.eventId in current) {
            TextButton(enabled = !saving, onClick = { supersedes = event.eventId; action = event.action; note = event.note }) { Text(menuText("この記録を訂正")) }
        }
        HorizontalDivider()
    }
    if (canEdit) {
        Text(menuText(if (supersedes == null) "対応記録を追加" else "自分の記録を訂正"), style = MaterialTheme.typography.titleMedium)
        if (supersedes != null) TextButton(enabled = !saving, onClick = { supersedes = null; action = CareAction.CHECKING; note = "" }) { Text(menuText("訂正をやめて新規記録にする")) }
        CareAction.entries.filter { it != CareAction.REVOKED || supersedes != null }.forEach { option ->
            FilterChip(enabled = !saving, selected = action == option, onClick = { action = option }, label = { Text(option.label()) })
        }
        OutlinedTextField(note, { if (it.length <= 500) note = it }, enabled = !saving, label = { Text(menuText("確認内容・訂正理由")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Button(enabled = !saving, onClick = { model.saveCare(resident.originId, action, note, supersedes) { saved -> supersedes = saved.eventId } }, modifier = Modifier.fillMaxWidth()) { Text(menuText(if (saving) "保存しています…" else "対応記録を保存")) }
        OperationFeedback(model, model.careSaveKey(resident.originId))
    } else Text(menuText("対応記録は閲覧のみ"))
}
