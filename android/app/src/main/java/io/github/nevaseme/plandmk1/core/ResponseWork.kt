package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable enum class TargetKind { PERSON, REPORT }
@Serializable data class ResponseTarget(val kind:TargetKind,val id:String)
@Serializable enum class WorkStatus { UNASSIGNED, PLANNED, IN_PROGRESS, DONE }
@Serializable enum class Urgency { UNSET, LOW, MEDIUM, HIGH }
@Serializable enum class InjuryState { UNKNOWN, INJURED, UNINJURED }
@Serializable enum class ActivityState { UNSET, AVAILABLE, WORKING, UNAVAILABLE }

@Serializable sealed class ResponseChange {
    @Serializable @SerialName("INJURY") data class Injury(val target:ResponseTarget,val state:InjuryState):ResponseChange()
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable @SerialName("REPORT") data class Report(val reportId:String,val category:String,val latitude:Double,val longitude:Double,val description:String,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val regionIds:List<String> = emptyList(),
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val cancelled:Boolean = false,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val cancelReason:String = "",
    ):ResponseChange()
    @Serializable @SerialName("ASSIGNMENT") data class Assignment(val target:ResponseTarget,val memberId:String,val active:Boolean):ResponseChange()
    @Serializable @SerialName("PROGRESS") data class Progress(val target:ResponseTarget,val status:WorkStatus,val result:String):ResponseChange()
    @Serializable @SerialName("PRIORITY") data class Priority(val target:ResponseTarget,val urgency:Urgency,val reason:String):ResponseChange()
    @Serializable @SerialName("AVAILABILITY") data class Availability(val state:ActivityState):ResponseChange()
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable @SerialName("MATERIAL") data class Material(val target:ResponseTarget,val requestId:String,val name:String,val unit:String,val needed:Double,val arranged:Double,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val cancelled:Boolean=false,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val cancelReason:String="",
    ):ResponseChange() {
        val shortfall:Double get()=if(cancelled)0.0 else (needed-arranged).coerceAtLeast(0.0)
    }
}
@Serializable data class ResponseEvent(
    val groupId:String,val eventId:String,val actorDeviceId:String,val sequence:Long,val operatedAt:Long,
    val incidentId:String?,val change:ResponseChange,val replaces:List<String> = emptyList(),
)

fun ResponseChange.target():ResponseTarget?=when(this) {
    is ResponseChange.Report->ResponseTarget(TargetKind.REPORT,reportId)
    is ResponseChange.Assignment->target
    is ResponseChange.Progress->target
    is ResponseChange.Priority->target
    is ResponseChange.Material->target
    is ResponseChange.Injury->target
    is ResponseChange.Availability->null
}
fun responseKey(roster:MemberRoster,event:ResponseEvent):String {
    val value=when(val c=event.change) {
        is ResponseChange.Report->"report:${c.reportId}"
        is ResponseChange.Assignment->"assignment:${c.target.kind}:${c.target.id}:${c.memberId}"
        is ResponseChange.Progress->"progress:${c.target.kind}:${c.target.id}"
        is ResponseChange.Priority->"priority:${c.target.kind}:${c.target.id}"
        is ResponseChange.Material->"material:${c.requestId}"
        is ResponseChange.Injury->"injury:${c.target.kind}:${c.target.id}"
        is ResponseChange.Availability->"availability:${roster.memberForDevice(event.actorDeviceId).memberId}"
    }
    return "${event.groupId}:${event.incidentId.orEmpty()}:$value"
}
fun validateResponseShape(roster:MemberRoster,event:ResponseEvent) {
    require(Json {encodeDefaults=true}.encodeToString(event).encodeToByteArray().size<=22_000) {"記録が通信可能なサイズを超えています。本文または訂正元の数を減らしてください"}
    require(event.groupId==roster.groupId && recordIdPattern.matches(event.eventId))
    roster.memberForDevice(event.actorDeviceId)
    require(event.sequence in 1..MAX_SEQUENCE && event.operatedAt in 0..MAX_SEQUENCE)
    require(event.replaces.distinct().size==event.replaces.size && event.replaces.all {recordIdPattern.matches(it)&&it!=event.eventId})
    if(event.change is ResponseChange.Availability)require(event.incidentId==null)
    else require(event.incidentId?.let(recordIdPattern::matches)==true)
    event.change.target()?.let {require(recordIdPattern.matches(it.id))}
    when(val c=event.change) {
        is ResponseChange.Report->{
            require(c.category.isNotBlank()&&c.description.isNotBlank()&&c.latitude.isFinite()&&c.latitude in -85.0..85.0&&c.longitude.isFinite()&&c.longitude in -180.0..180.0)
            require(c.regionIds.distinct().size==c.regionIds.size&&c.regionIds.all {it.matches(Regex("[A-Za-z0-9_-]{1,80}"))}) {"地域IDを確認してください"}
            require(!c.cancelled||c.cancelReason.isNotBlank()) {"取消理由を入力してください"}
        }
        is ResponseChange.Assignment->require(roster.members.any {it.memberId==c.memberId}&&(c.memberId==roster.memberForDevice(event.actorDeviceId).memberId||roster.canCoordinate(event.actorDeviceId)))
        is ResponseChange.Progress->require(c.status!=WorkStatus.DONE || c.result.isNotBlank()) {"対応結果を入力してください"}
        is ResponseChange.Priority->{
            require(c.urgency==Urgency.UNSET || c.reason.isNotBlank()) {"緊急度の理由を入力してください"}
        }
        is ResponseChange.Material->{require(recordIdPattern.matches(c.requestId)&&c.name.isNotBlank()&&c.unit.isNotBlank()&&c.needed.isFinite()&&c.arranged.isFinite()&&c.needed>=0&&c.arranged>=0);require(!c.cancelled||c.cancelReason.isNotBlank()) {"取消理由を入力してください"}}
        is ResponseChange.Injury->require(c.target.kind==TargetKind.PERSON)
        is ResponseChange.Availability->Unit
    }
}

data class StartWorkChange(val change:ResponseChange,val replaces:List<String>)

/** Use the immutable original event, never the latest editor, to establish report ownership. */
fun canManageResponseTarget(roster:MemberRoster,history:List<ResponseEvent>,actorDeviceId:String,target:ResponseTarget,incidentId:String?):Boolean {
    val member=roster.members.find {actorDeviceId in it.deviceIds}?:return false
    if(roster.canCoordinate(actorDeviceId)||target.kind==TargetKind.PERSON)return true
    val original=history.find {it.eventId==target.id&&it.groupId==roster.groupId&&it.incidentId==incidentId&&(it.change as? ResponseChange.Report)?.reportId==target.id}?:return false
    return roster.members.find {original.actorDeviceId in it.deviceIds}?.memberId==member.memberId
}
fun canEditMaterial(roster:MemberRoster,history:List<ResponseEvent>,actorDeviceId:String,material:ResponseChange.Material,incidentId:String?):Boolean {
    val member=roster.members.find {actorDeviceId in it.deviceIds}?:return false
    if(roster.canCoordinate(actorDeviceId))return true
    val original=history.find {it.eventId==material.requestId&&it.groupId==roster.groupId&&it.incidentId==incidentId&&it.change is ResponseChange.Material}
    if(original!=null&&roster.members.find {original.actorDeviceId in it.deviceIds}?.memberId==member.memberId)return true
    return material.target.kind==TargetKind.REPORT&&canManageResponseTarget(roster,history,actorDeviceId,material.target,incidentId)
}
/** One local command does not resolve concurrent progress or reopen completed work. */
fun startWorkChanges(roster:MemberRoster,heads:List<ResponseEvent>,actorDeviceId:String,target:ResponseTarget):List<StartWorkChange> {
    val member=roster.memberForDevice(actorDeviceId)
    require(recordIdPattern.matches(target.id))
    val relevant=heads.filter {it.change.target()==target}
    val assignments=relevant.filter {(it.change as? ResponseChange.Assignment)?.memberId==member.memberId}
    val progress=relevant.filter {it.change is ResponseChange.Progress}
    val changes=mutableListOf<StartWorkChange>()
    if(assignments.size!=1||(assignments.single().change as ResponseChange.Assignment).active.not())
        changes+=StartWorkChange(ResponseChange.Assignment(target,member.memberId,true),assignments.map {it.eventId})
    if(progress.isEmpty()||(progress.size==1&&(progress.single().change as ResponseChange.Progress).status in listOf(WorkStatus.UNASSIGNED,WorkStatus.PLANNED)))
        changes+=StartWorkChange(ResponseChange.Progress(target,WorkStatus.IN_PROGRESS,""),progress.map {it.eventId})
    return changes
}

private fun authorizeResponse(roster:MemberRoster,history:List<ResponseEvent>,event:ResponseEvent) {
    validateResponseShape(roster,event)
    val member=roster.memberForDevice(event.actorDeviceId)
    val coordinator=roster.canCoordinate(event.actorDeviceId)
    val parents=event.replaces.map {id->requireNotNull(history.find {it.eventId==id&&it.groupId==event.groupId}) {"訂正元の記録がありません"}}
    require(parents.all {responseKey(roster,it)==responseKey(roster,event)}) {"異なる対象の記録は訂正できません"}
    fun creator(id:String):GroupMember {
        if(event.eventId==id) {require(event.replaces.isEmpty());return member}
        val root=requireNotNull(history.find {it.eventId==id&&it.groupId==event.groupId&&it.incidentId==event.incidentId}) {"元の記録がありません"}
        require(responseKey(roster,root)==responseKey(roster,event))
        require(event.replaces.isNotEmpty()) {"訂正元を指定してください"}
        return roster.memberForDevice(root.actorDeviceId)
    }
    when(val c=event.change) {
        is ResponseChange.Report->require(creator(c.reportId).memberId==member.memberId||coordinator) {"通報を訂正する権限がありません"}
        is ResponseChange.Assignment->{require(c.memberId==member.memberId||coordinator) {"他の人の担当は班長・管理者が調整します"};require(canManageResponseTarget(roster,history,event.actorDeviceId,c.target,event.incidentId)) {"この通報は閲覧のみです"}}
        is ResponseChange.Priority->require(roster.couldTriage(event.actorDeviceId)) {"負傷登録・緊急度設定の権限がありません"}
        is ResponseChange.Material->{
            val owner=creator(c.requestId)
            require(if(event.eventId==c.requestId)canManageResponseTarget(roster,history,event.actorDeviceId,c.target,event.incidentId) else owner.memberId==member.memberId||canEditMaterial(roster,history,event.actorDeviceId,c,event.incidentId)) {"資材要請を訂正する権限がありません"}
            val original=history.find {it.eventId==c.requestId&&it.groupId==event.groupId}?.change as? ResponseChange.Material
            if(original!=null)require(original.target==c.target) {"異なる対象の資材は別の要請として登録してください"}
        }
        is ResponseChange.Availability->require(parents.all {roster.memberForDevice(it.actorDeviceId).memberId==member.memberId})
        is ResponseChange.Progress->require(canManageResponseTarget(roster,history,event.actorDeviceId,c.target,event.incidentId)) {"この通報は閲覧のみです"}
        is ResponseChange.Injury->Unit
    }
}

/** Concurrent heads remain visible; an explicit correction can replace all heads the author has reviewed. */
fun responseHeads(roster:MemberRoster,events:List<ResponseEvent>):List<ResponseEvent> {
    val waiting=events.filter {it.groupId==roster.groupId}.sortedBy {it.eventId}.toMutableList()
    val accepted=mutableListOf<ResponseEvent>()
    var changed=true
    while(changed) {
        changed=false
        val iterator=waiting.iterator()
        while(iterator.hasNext()) {
            val event=iterator.next()
            try {authorizeResponse(roster,accepted,event)}catch(_:IllegalArgumentException){continue}
            accepted.add(event);iterator.remove();changed=true
        }
    }
    val replaced=accepted.flatMap {it.replaces}.toSet()
    return accepted.filter {it.eventId !in replaced}.sortedBy {it.eventId}
}
fun validateResponseCommand(roster:MemberRoster,history:List<ResponseEvent>,event:ResponseEvent) {
    if(event.change is ResponseChange.Injury||event.change is ResponseChange.Priority)require(roster.canTriage(event.actorDeviceId)) {"負傷登録・緊急度設定の権限がありません"}
    authorizeResponse(roster,history,event)
    val heads=responseHeads(roster,history).map {it.eventId}.toSet()
    require(event.replaces.all {it in heads}) {"記録が更新されています。最新の内容を確認してください"}
}
