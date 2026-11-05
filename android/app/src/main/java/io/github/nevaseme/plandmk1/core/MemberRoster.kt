package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val recordIdPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
internal const val MAX_SEQUENCE = 9_007_199_254_740_991L
@Serializable enum class MemberRole { MEMBER, LEADER, ADMIN }
@Serializable data class GroupMember(val memberId:String, val activityName:String, val role:MemberRole, val deviceIds:List<String>, val canTriage:Boolean=false)
fun GroupMember.preserves(old:GroupMember)=memberId==old.memberId&&activityName==old.activityName&&role==old.role&&deviceIds.take(old.deviceIds.size)==old.deviceIds&&(old.role==MemberRole.MEMBER||deviceIds.size==old.deviceIds.size)
@Serializable data class RosterRevision(val revision:Long,val members:List<GroupMember>)
@Serializable data class MemberRoster(val version:Int=1, val groupId:String, val members:List<GroupMember>,val revision:Long=0,val history:List<RosterRevision> = emptyList()) {
    fun validate() {
        require(version==1 && groupId.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "参加者設定の形式が不正です" }
        require(members.isNotEmpty() && members.any { it.role==MemberRole.ADMIN }) { "参加者と管理者が必要です" }
        require(members.map {it.memberId}.distinct().size==members.size) { "同じ参加者が重複しています" }
        val devices=members.flatMap {it.deviceIds}
        require(devices.distinct().size==devices.size) { "端末が複数の参加者に登録されています" }
        members.forEach {
            require(recordIdPattern.matches(it.memberId) && it.deviceIds.isNotEmpty() && it.deviceIds.all(recordIdPattern::matches))
            require(it.activityName.isNotBlank() && it.activityName.length<=60)
        }
        require(revision in 0..MAX_SEQUENCE && history.map {it.revision}.distinct().size==history.size)
        var previous=emptyList<GroupMember>()
        history.sortedBy {it.revision}.forEach {snapshot->
            require(snapshot.revision in 0..revision)
            if(snapshot.revision==revision)require(snapshot.members==members)
            MemberRoster(groupId=groupId,members=snapshot.members).validate()
            require(snapshot.members.all {old->members.any {it.preserves(old)}} && previous.all {old->snapshot.members.any {it.preserves(old)}}) {"参加者の履歴は変更できません"}
            previous=snapshot.members
        }
    }
    fun membersAt(value:Long):List<GroupMember>?=if(value==revision)members else history.find {it.revision==value}?.members
    fun memberForDevice(device:String):GroupMember = requireNotNull(members.find {device in it.deviceIds}) { "参加者に登録されていない端末です" }
    fun canTriage(device:String)=memberForDevice(device).let {it.role==MemberRole.ADMIN||it.canTriage}
    fun couldTriage(device:String)=canTriage(device)||memberForDevice(device).role==MemberRole.LEADER||history.any {h->h.members.any {device in it.deviceIds&&(it.role==MemberRole.ADMIN||it.canTriage)}}
    fun canCoordinate(device:String) = memberForDevice(device).role in setOf(MemberRole.LEADER,MemberRole.ADMIN)
    fun encode():String {validate();return Json.encodeToString(this)}
    companion object { fun decode(text:String)=Json.decodeFromString<MemberRoster>(text).also {it.validate()} }
}

/** Server roster additions never rewrite existing identities, authority, or known historical quorum. */
fun validateRosterUpdate(previous:MemberRoster?,candidate:MemberRoster,ownDeviceId:String) {
    candidate.validate();candidate.memberForDevice(ownDeviceId)
    if(previous==null)return
    require(previous.groupId==candidate.groupId && candidate.revision>=previous.revision)
    if(previous.revision==candidate.revision) {require(previous==candidate) {"同じ参加者版の内容が異なります"};return}
    require(previous.members.all {old->candidate.members.any {it.preserves(old)}}) {"既存の参加者情報は変更できません"}
    require(candidate.membersAt(previous.revision)==previous.members) {"元の参加者版が保存されていません"}
    previous.history.forEach {require(candidate.membersAt(it.revision)==it.members) {"参加者の履歴は変更できません"}}
}
