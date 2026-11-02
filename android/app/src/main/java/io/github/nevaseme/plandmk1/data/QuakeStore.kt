package io.github.nevaseme.plandmk1.data

import androidx.room.*
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Entity(tableName="member_rosters",primaryKeys=["groupId"])
data class RosterRow(val groupId:String,val payload:String)
@Entity(tableName="quake_events",primaryKeys=["groupId","eventId"])
data class QuakeRow(val groupId:String,val eventId:String,val payload:String)
@Dao interface QuakeDao {
    @Query("SELECT * FROM member_rosters WHERE groupId=:group") suspend fun roster(group:String):RosterRow?
    @Query("SELECT * FROM member_rosters WHERE groupId=:group") fun observeRoster(group:String):Flow<RosterRow?>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun putRoster(row:RosterRow)
    @Update suspend fun updateRoster(row:RosterRow):Int
    @Query("SELECT * FROM quake_events WHERE groupId=:group ORDER BY eventId") suspend fun events(group:String):List<QuakeRow>
    @Query("SELECT * FROM quake_events WHERE groupId=:group ORDER BY eventId") fun observe(group:String):Flow<List<QuakeRow>>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun put(row:QuakeRow)
}

class QuakeStore(private val db:PlanDatabase) {
    /** Idempotent durable receive; a missing roster never creates an administrator or a deferred group join. */
    suspend fun receiveEew(receipt:io.github.nevaseme.plandmk1.eew.EewReceipt):String?=db.withTransaction {
        val target=receipt.target?:return@withTransaction null
        val warning=receipt.warning
        if(!receipt.actionable || warning.test || warning.warningFor("京都府南部")==null || receipt.receivedAt-warning.issuedAt>180_000)return@withTransaction null
        val roster=roster(target.groupId)?:return@withTransaction null
        if(roster.members.none {target.deviceId in it.deviceIds})return@withTransaction null
        val previous=events(target.groupId)
        val id=UUID.nameUUIDFromBytes("eew:${warning.source}:${warning.messageId}:${target.deviceId}".toByteArray(Charsets.UTF_8)).toString()
        val old=previous.find {it.eventId==id}
        val proof=old?:run {
            val evidence=EewEvidence(warning.source,warning.eventId,warning.issuedAt,receipt.receivedAt,
                startsNewPeriod=quakeStates(roster,previous).none {!it.closed&&!it.formation.training})
            QuakeEvent(target.groupId,id,target.deviceId,receipt.receivedAt,QuakeAction.EEW,false,targetId=evidence.incidentId(),eew=evidence).also {merge(it)}
        }
        // This decision commits with the proof. A crash after DB commit can repair selection, while
        // completed receipts and later serials never overwrite a user's deliberate history selection.
        val state=quakeStates(roster,events(target.groupId)).find {proof.targetId in it.incidentIds}
        if(proof.eew?.startsNewPeriod==true && state?.closed==false)state.formation.incidentId else null
    }
    suspend fun roster(group:String)=db.quakes().roster(group)?.let {MemberRoster.decode(it.payload)}
    suspend fun updateRoster(candidate:MemberRoster,ownDeviceId:String)=db.withTransaction {
        val previous=roster(candidate.groupId);validateRosterUpdate(previous,candidate,ownDeviceId)
        val row=RosterRow(candidate.groupId,candidate.encode())
        if(previous==null)db.quakes().putRoster(row) else if(previous!=candidate)check(db.quakes().updateRoster(row)==1)
    }
    fun observeRoster(group:String)=db.quakes().observeRoster(group).map {it?.let {MemberRoster.decode(it.payload)}}
    suspend fun importRoster(text:String,ownDeviceId:String):MemberRoster=db.withTransaction {
        val candidate=MemberRoster.decode(text);candidate.memberForDevice(ownDeviceId)
        val previous=roster(candidate.groupId)
        require(previous==null || previous==candidate) {"この試験グループには異なる参加者設定が保存されています"}
        if(previous==null)db.quakes().putRoster(RosterRow(candidate.groupId,candidate.encode()))
        candidate
    }
    suspend fun events(group:String)=db.quakes().events(group).map {Json.decodeFromString<QuakeEvent>(it.payload)}
    fun observe(group:String)=db.quakes().observe(group).map {rows->rows.map {Json.decodeFromString<QuakeEvent>(it.payload)}}
    suspend fun states(group:String)=roster(group)?.let {quakeStates(it,events(group))}.orEmpty()
    suspend fun evaluate(group:String,localDeviceId:String,at:Long):List<QuakeState> =db.withTransaction {
        val roster=requireNotNull(roster(group));roster.memberForDevice(localDeviceId)
        formationCandidates(roster,events(group)).forEach {merge(it.event(UUID.randomUUID().toString(),localDeviceId,at))}
        states(group)
    }
    private suspend fun materialize(group:String) {
        val roster=requireNotNull(roster(group));val rows=events(group)
        quakeStates(roster,rows).forEach {state->
          state.incidentIds.forEach {incidentId->
            val evidence=rows.firstOrNull {it.action==QuakeAction.EEW && it.targetId==incidentId}?.eew
            val start=if(evidence!=null)eewIncidentStart(group,evidence) else {
                val report=rows.first {it.eventId==incidentId}
                IncidentEvent(group,report.eventId,report.eventId,report.actorDeviceId,roster.memberForDevice(report.actorDeviceId).activityName,1,report.operatedAt,IncidentKind.START,report.title,report.area,report.training)
            }
            if(db.incidents().get(group,start.eventId)==null)db.incidents().put(IncidentRow(group,start.eventId,Json.encodeToString(start)))
          }
        }
    }

    /** Received history can arrive before its referenced report; it is projected only when its proof is available. */
    suspend fun merge(event:QuakeEvent):Boolean=db.withTransaction {
        val roster=requireNotNull(roster(event.groupId)) {"参加者設定を読み込んでください"}
        event.validate(roster)
        if(event.action==QuakeAction.END)require(roster.memberForDevice(event.actorDeviceId).role==MemberRole.ADMIN)
        val old=events(event.groupId).find {it.eventId==event.eventId}
        if(old!=null) {require(old==event) {"同じIDに異なる災害記録があります"};false}
        else {db.quakes().put(QuakeRow(event.groupId,event.eventId,Json.encodeToString(event)));materialize(event.groupId);true}
    }
    suspend fun command(event:QuakeEvent):List<QuakeState> =db.withTransaction {
        val roster=requireNotNull(roster(event.groupId)) {"参加者設定を読み込んでください"}
        val previous=events(event.groupId)
        val old=previous.find {it.eventId==event.eventId}
        if(old!=null) {require(old==event || (event.action==QuakeAction.END&&event.endedIncidentIds.isEmpty()&&old.copy(endedIncidentIds=emptyList())==event));return@withTransaction quakeStates(roster,previous)}
        val command=if(event.action==QuakeAction.END)event.copy(endedIncidentIds=quakeStates(roster,previous).find {event.targetId in it.incidentIds}?.incidentIds.orEmpty().sorted())else event
        validateQuakeCommand(roster,previous,command)
        merge(command)
        if(event.action==QuakeAction.REPORT)evaluate(event.groupId,event.actorDeviceId,event.operatedAt)
        quakeStates(roster,events(event.groupId))
    }
}
