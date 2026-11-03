package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrainingWireTest {
    private fun id()=UUID.randomUUID().toString()
    private val admin=id();private val member=id();private val group="training-wire"
    private val roster=MemberRoster(groupId=group,members=listOf(GroupMember(admin,"管理者",MemberRole.ADMIN,listOf(admin)),GroupMember(member,"参加者",MemberRole.MEMBER,listOf(member))))
    private fun start()=QuakeEvent(group,id(),admin,10,QuakeAction.TRAINING_START,true,title="訓練",area="架空地域")
    private inner class Store(val revision:Long=0):RecordStore {
        val quakes=mutableListOf<QuakeEvent>();val journal=mutableListOf<IncidentEvent>();val responses=mutableListOf<ResponseEvent>()
        var failSave=false
        override suspend fun rosterRevision(groupId:String)=revision
        override suspend fun all(groupId:String)=emptyList<Resident>()
        override suspend fun get(groupId:String,originId:String):Resident?=null
        override suspend fun merge(record:Resident)=false
        override suspend fun quakeEvents(groupId:String)=quakes.toList()
        override suspend fun mergeQuake(event:QuakeEvent):Boolean {check(!failSave);event.validate(roster);if(event in quakes)return false;quakes+=event;return true}
        override suspend fun incidents(groupId:String)=journal.toList()
        override suspend fun getIncident(groupId:String,eventId:String)=journal.find {it.eventId==eventId}
        override suspend fun mergeIncident(event:IncidentEvent):Boolean {if(event in journal)return false;journal+=event;return true}
        override suspend fun responseEvents(groupId:String)=responses.toList()
        override suspend fun mergeResponse(event:ResponseEvent):Boolean {if(event in responses)return false;responses+=event;return true}
    }
    @Test fun proofBoundaryRejectsOldVersionStartAndDefersEarlyEndAndAnswerWithoutAck()=runTest {
        val store=Store();val sent=mutableListOf<WireMessage>()
        val engine=SyncEngine(group,member,store,{_,bytes->sent+=Json.decodeFromString<WireMessage>(bytes.decodeToString())})
        engine.connected("A");sent.clear()
        suspend fun receive(message:WireMessage)=engine.receive("A",Json.encodeToString(message).encodeToByteArray())
        val start=start();val end=QuakeEvent(group,id(),admin,30,QuakeAction.END,true,start.eventId)
        val answer=IncidentEvent(group,id(),start.eventId,member,"参加者",1,20,IncidentKind.ANSWER,training=true,safety=Safety.SAFE)
        val response=ResponseEvent(group,id(),member,1,20,start.eventId,ResponseChange.Progress(ResponseTarget(TargetKind.PERSON,member),WorkStatus.DONE,"訓練確認"))
        for(version in listOf(6,7,8))receive(WireMessage(version,group,"QUAKE_RECORD",quake=start))
        receive(WireMessage(9,group,"QUAKE_RECORD",quake=end))
        receive(WireMessage(9,group,"INCIDENT_RECORD",incident=answer))
        receive(WireMessage(9,group,"RESPONSE_RECORD",response=response))
        assertTrue(store.quakes.isEmpty());assertTrue(store.journal.isEmpty());assertTrue(store.responses.isEmpty());assertTrue(sent.isEmpty())
        receive(WireMessage(9,group,"QUAKE_RECORD",quake=start))
        assertEquals(listOf(start),store.quakes);assertTrue(sent.any {it.type=="QUAKE_ACK"&&it.version==9})
        sent.clear()
        receive(WireMessage(9,group,"INCIDENT_RECORD",incident=answer));receive(WireMessage(9,group,"QUAKE_RECORD",quake=end))
        receive(WireMessage(9,group,"RESPONSE_RECORD",response=response))
        assertEquals(listOf(answer),store.journal);assertTrue(quakeStates(roster,store.quakes).single().closed)
        assertEquals(listOf(response),store.responses)
        assertTrue(sent.filter {it.type.startsWith("QUAKE_")||it.type.startsWith("INCIDENT_")||it.type.startsWith("RESPONSE_")}.all {it.version==9})
        assertTrue(quakeStates(roster,Json.decodeFromString<List<QuakeEvent>>(Json.encodeToString(store.quakes))).single().closed)
        sent.clear();receive(WireMessage(3,group,"INCIDENT_REQUEST",entries=listOf(VersionEntry(answer.eventId,1))))
        assertEquals(9,sent.single().version)
    }
    @Test fun v9RequestIsNeverDowngradedByRosterV8AndFailedSaveIsNotAcknowledged()=runTest {
        val store=Store(1);val sent=mutableListOf<WireMessage>()
        val engine=SyncEngine(group,member,store,{_,bytes->sent+=Json.decodeFromString<WireMessage>(bytes.decodeToString())})
        engine.connected("A");sent.clear();val start=start()
        suspend fun receive(message:WireMessage)=engine.receive("A",Json.encodeToString(message).encodeToByteArray())
        receive(WireMessage(9,group,"QUAKE_SUMMARY",entries=listOf(VersionEntry(start.eventId,1))))
        assertEquals("QUAKE_REQUEST",sent.single().type);assertEquals(9,sent.single().version)
        sent.clear();store.failSave=true
        receive(WireMessage(9,group,"QUAKE_RECORD",quake=start))
        assertTrue(sent.isEmpty());assertTrue(store.quakes.isEmpty())
        store.failSave=false;receive(WireMessage(9,group,"QUAKE_RECORD",quake=start))
        assertTrue(sent.any {it.type=="QUAKE_ACK"&&it.version==9})
        val reopened=Json.decodeFromString<List<QuakeEvent>>(Json.encodeToString(store.quakes))
        assertEquals(quakeStates(roster,store.quakes),quakeStates(roster,reopened))
    }
    @Test fun extendedResponseUsesV10WithoutDowngradingOrChangingLegacyReports()=runTest {
        val store=Store();val sent=mutableListOf<WireMessage>();val training=start();store.quakes+=training
        val reportId=id();val basic=ResponseEvent(group,reportId,admin,1,20,training.eventId,ResponseChange.Report(reportId,"道路閉塞",35.0,139.0,"倒木"))
        val cancelled=basic.copy(eventId=id(),sequence=2,replaces=listOf(reportId),change=(basic.change as ResponseChange.Report).copy(cancelled=true,cancelReason="重複"))
        val planned=basic.copy(eventId=id(),sequence=3,change=ResponseChange.Progress(ResponseTarget(TargetKind.REPORT,reportId),WorkStatus.PLANNED,""))
        store.responses+=listOf(basic,cancelled,planned)
        val engine=SyncEngine(group,member,store,{_,bytes->sent+=Json.decodeFromString<WireMessage>(bytes.decodeToString())})
        engine.connected("A");sent.clear()
        engine.receive("A",Json.encodeToString(WireMessage(9,group,"RESPONSE_REQUEST",entries=store.responses.map {VersionEntry(it.eventId,1)})).encodeToByteArray())
        assertEquals(listOf(9,10,10),sent.filter {it.type=="RESPONSE_RECORD"}.map {it.version})
        val receiver=Store();receiver.quakes+=training;val replies=mutableListOf<WireMessage>()
        val other=SyncEngine(group,member,receiver,{_,bytes->replies+=Json.decodeFromString<WireMessage>(bytes.decodeToString())})
        other.connected("B");replies.clear()
        for(message in sent.filter {it.type=="RESPONSE_RECORD"})other.receive("B",Json.encodeToString(message).encodeToByteArray())
        assertEquals(store.responses,receiver.responses)
        assertEquals(listOf(9,10,10),replies.filter {it.type=="RESPONSE_ACK"}.map {it.version})
        val legacy=Json {encodeDefaults=true}.encodeToString(basic)
        assertFalse(legacy.contains("cancelled"));assertFalse(legacy.contains("regionIds"));assertFalse(legacy.contains("cancelReason"))
    }

    @Test fun materialRecordsUseV18AndKeepCancellationAndUnitCorrectionAcrossSync()=runTest {
        val store=Store();val sent=mutableListOf<WireMessage>();val training=start();store.quakes+=training
        val request=id();val material=ResponseChange.Material(ResponseTarget(TargetKind.PERSON,member),request,"水","本",12.0,6.0)
        val original=ResponseEvent(group,request,admin,1,20,training.eventId,material)
        val corrected=original.copy(eventId=id(),sequence=2,replaces=listOf(request),change=material.copy(unit="箱",needed=2.0,arranged=1.0))
        val cancelled=corrected.copy(eventId=id(),sequence=3,replaces=listOf(corrected.eventId),change=(corrected.change as ResponseChange.Material).copy(cancelled=true,cancelReason="不要"))
        store.responses+=listOf(original,corrected,cancelled)
        val sender=SyncEngine(group,member,store,{_,bytes->sent+=Json.decodeFromString<WireMessage>(bytes.decodeToString())})
        sender.connected("A");sent.clear()
        sender.receive("A",Json.encodeToString(WireMessage(9,group,"RESPONSE_REQUEST",entries=store.responses.map{VersionEntry(it.eventId,1)})).encodeToByteArray())
        val records=sent.filter{it.type=="RESPONSE_RECORD"};assertEquals(3,records.size);assertTrue(records.all{it.version==18})
        val receiver=Store();receiver.quakes+=training;val replies=mutableListOf<WireMessage>()
        val other=SyncEngine(group,member,receiver,{_,bytes->replies+=Json.decodeFromString<WireMessage>(bytes.decodeToString())})
        other.connected("B");replies.clear()
        records.forEach{other.receive("B",Json.encodeToString(it).encodeToByteArray())}
        assertEquals(store.responses,receiver.responses);assertTrue(replies.filter{it.type=="RESPONSE_ACK"}.all{it.version==18})
        assertEquals(3,replies.count{it.type=="RESPONSE_ACK"})
    }
}
