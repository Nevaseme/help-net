package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CloudSyncTest {
    private val me=UUID.randomUUID().toString();private val group=UUID.randomUUID().toString()
    private val roster=MemberRoster(groupId=group,members=listOf(GroupMember(me,"試験",MemberRole.ADMIN,listOf(me))))
    private val envelope=CloudRosterEnvelope(groupId=group,rosterRevision=1,roster=roster,rosterHistory=listOf(CloudRosterSnapshot(1,roster)))
    private val session=CloudSession("https://example.invalid",group,me,me,"token",MemberRole.ADMIN)
    private fun record(owner:String=me)=CloudRecord("residents",buildJsonObject {put("groupId",group);put("originId",owner);put("revision",1);put("name","架空")})
    private class Checkpoint:CloudCheckpoint {
        var cursorValue=0L;val ack=mutableSetOf<String>();var failCommit=false
        override fun cursor()=cursorValue
        override fun acknowledged(record:CloudRecord)=record.ackKey() in ack
        override fun acknowledge(records:List<CloudRecord>){ack+=records.map {it.ackKey()}}
        override fun commitPage(cursor:Long,records:List<CloudRecord>){if(failCommit)error("checkpoint disk full");acknowledge(records);cursorValue=cursor}
    }
    private class Journal:CloudJournal {
        val rows=mutableListOf<CloudRecord>();var fail=false;var revision=0L
        override suspend fun updateRoster(envelope:CloudRosterEnvelope,deviceId:String){revision=envelope.rosterRevision}
        override suspend fun records()=rows.toList()
        override suspend fun mergePage(records:List<CloudRecord>){if(fail)error("disk full");rows.addAll(records.filter {r->rows.none {it.ackKey()==r.ackKey()}})}
    }
    private inner class Api:CloudApi {
        var failPush=false;var wrongAck=false;var rejectKey:String?=null;var page=CloudPage(emptyList(),0,false,1);val pushed=mutableListOf<CloudRecord>();var rosterReads=0
        var rejectStatus=409;var rejectCode="record_conflict"
        var dependencyStatus=409;var dependencyCode="missing_incident"
        val dependencies=mutableMapOf<String,Set<String>>();val stored=mutableSetOf<String>()
        override suspend fun roster(session:CloudSession):CloudRosterEnvelope {rosterReads++;return if(rosterReads>1)envelope.copy(rosterRevision=2) else envelope}
        override suspend fun pull(session:CloudSession,after:Long)=if(after==0L)page else CloudPage(emptyList(),after,false,1)
        override suspend fun push(session:CloudSession,records:List<CloudRecord>):CloudPushResult {
            if(failPush)error("offline");pushed+=records
            if(records.any {it.key()==rejectKey})throw CloudHttpException(rejectStatus,rejectCode)
            // Model the server's atomic ordered batch: no partial commit on a missing prerequisite.
            val staged=stored.toMutableSet()
            for(record in records) {
                if(!dependencies[record.key()].orEmpty().all {it in staged})throw CloudHttpException(dependencyStatus,dependencyCode)
                staged+=record.key()
            }
            stored+=staged
            return CloudPushResult(records.mapIndexed {i,r->CloudAck(r.collection,if(wrongAck)"unrelated" else r.key(),i+1L,"stored",1)},1)
        }
    }
    private fun event(collection:String,action:String)=CloudRecord(collection,buildJsonObject {
        put("groupId",group);put("eventId",UUID.randomUUID().toString())
        put(if(collection=="incidents")"authorId" else "actorDeviceId",me)
        put(if(collection=="incidents")"kind" else "action",action)
    })
    @Test fun formationAndAnswerSortedBeforeEvidenceCompleteWithoutManualSecondSync()=runTest {
        val api=Api();val local=Journal();val checkpoint=Checkpoint()
        val report=event("quakes","REPORT");val form=event("quakes","FORM");val answer=event("incidents","ANSWER")
        api.dependencies[form.key()]=setOf(report.key());api.dependencies[answer.key()]=setOf(form.key())
        local.rows+=listOf(answer,form,report)
        val result=CloudSyncEngine(session,api,local,checkpoint).sync()
        assertEquals(3,result.uploaded);assertEquals(0,result.rejected)
        assertTrue(listOf(report,form,answer).all {checkpoint.acknowledged(it)})
    }
    @Test fun missingEvidenceStillRetriesInBackgroundAfterOtherRecordsAreAcknowledged()=runTest {
        val api=Api();val local=Journal();val checkpoint=Checkpoint()
        val answer=event("incidents","ANSWER");val evidence=event("quakes","FORM");val profile=record()
        api.dependencies[answer.key()]=setOf(evidence.key());local.rows+=listOf(answer,profile)
        val failure=try {CloudSyncEngine(session,api,local,checkpoint).sync();null}catch(error:CloudHttpException){error}
        assertNotNull("A missing incident must retain an automatic retry",failure)
        assertEquals("missing_incident",failure!!.code);assertTrue(retryCloudFailure(failure))
        assertTrue(checkpoint.acknowledged(profile));assertFalse(checkpoint.acknowledged(answer))
        local.rows+=evidence
        val recovered=CloudSyncEngine(session,api,local,checkpoint).sync()
        assertEquals(2,recovered.uploaded);assertEquals(0,recovered.rejected)
        assertTrue(checkpoint.acknowledged(answer))
    }
    @Test fun correctionBeforeItsRootRetriesOnProgressButPermanentConflictStops()=runTest {
        val api=Api();val local=Journal();val checkpoint=Checkpoint()
        val correction=event("responses","CORRECTION");val root=event("responses","REPORT");val conflict=record()
        api.dependencyStatus=400;api.dependencyCode="invalid_record"
        api.dependencies[correction.key()]=setOf(root.key());api.rejectKey=conflict.key()
        local.rows+=listOf(correction,root,conflict)
        val result=CloudSyncEngine(session,api,local,checkpoint).sync()
        assertEquals(2,result.uploaded);assertEquals(1,result.rejected)
        assertTrue(checkpoint.acknowledged(correction));assertTrue(checkpoint.acknowledged(root));assertFalse(checkpoint.acknowledged(conflict))
        assertTrue("Retry work must stop when no new acknowledgements are made",api.pushed.size<=20)
    }
    @Test fun permanentForbiddenRecordDoesNotCreateBackgroundRetryLoop()=runTest {
        val api=Api();val local=Journal();val checkpoint=Checkpoint();val forbidden=record()
        api.rejectKey=forbidden.key();api.rejectStatus=403;api.rejectCode="invalid_relay_proof";local.rows+=forbidden
        val result=CloudSyncEngine(session,api,local,checkpoint).sync()
        assertEquals(1,result.rejected);assertEquals(0,result.uploaded)
        assertEquals(1,api.pushed.size);assertFalse(checkpoint.acknowledged(forbidden))
    }
    @Test fun signedForeignMeshRecordUploadsWithoutOriginalAuthorPresent()=runTest {
        val api=Api();val local=Journal();val checkpoint=Checkpoint()
        val foreign=record(UUID.randomUUID().toString())
        val wire=buildJsonObject {
            put("collection",foreign.collection);put("record",foreign.record)
            put("relayProof",buildJsonObject {put("version",1);put("keyId","fixture-key");put("body","fixture-body");put("signature","fixture-signature")})
        }
        local.rows+=Json {ignoreUnknownKeys=true}.decodeFromJsonElement<CloudRecord>(wire)
        val result=CloudSyncEngine(session,api,local,checkpoint).sync()
        assertEquals(1,result.uploaded)
        assertEquals(0,result.unsentForeign)
        assertEquals(foreign.owner(),api.pushed.single().owner())
        assertTrue(checkpoint.acknowledged(foreign))
    }
    @Test fun pageCursorNeverAdvancesBeforeLocalDurableSaveAndRetryReplays()=runTest {
        val api=Api();val local=Journal();val checkpoint=Checkpoint();val r=record()
        api.page=CloudPage(listOf(CloudStoredRecord(1,r.collection,r.record,1)),1,false,1);local.fail=true
        try {CloudSyncEngine(session,api,local,checkpoint).sync();fail()}catch(_:IllegalStateException){}
        assertEquals(0L,checkpoint.cursor());assertTrue(local.rows.isEmpty())
        local.fail=false;CloudSyncEngine(session,api,local,checkpoint).sync();assertEquals(1L,checkpoint.cursor());assertEquals(1,local.rows.size);assertTrue(api.pushed.isEmpty())
    }
    @Test fun failedUploadRemainsPendingAndForeignMeshRecordsAreExplicitlyUnsent()=runTest {
        val api=Api();val local=Journal();val checkpoint=Checkpoint();val own=record();val foreign=record(UUID.randomUUID().toString());local.rows+=listOf(own,foreign)
        api.failPush=true;try {CloudSyncEngine(session,api,local,checkpoint).sync();fail()}catch(_:IllegalStateException){}
        assertFalse(checkpoint.acknowledged(own));assertEquals(2,local.rows.size)
        api.failPush=false;val result=CloudSyncEngine(session,api,local,checkpoint).sync()
        assertTrue(checkpoint.acknowledged(own));assertFalse(checkpoint.acknowledged(foreign));assertEquals(1,result.unsentForeign);assertEquals(listOf(own),api.pushed)
    }
    @Test fun malformedCursorAndUnrelatedAcknowledgementCannotConfirmRecords()=runTest {
        val local=Journal();val checkpoint=Checkpoint();val api=Api();val own=record();local.rows+=own
        api.page=CloudPage(listOf(CloudStoredRecord(4,own.collection,own.record,1)),3,false,1)
        try{CloudSyncEngine(session,api,local,checkpoint).sync();fail()}catch(_:IllegalArgumentException){}
        assertEquals(0L,checkpoint.cursor());assertFalse(checkpoint.acknowledged(own))
        api.page=CloudPage(emptyList(),0,false,1);api.wrongAck=true
        try{CloudSyncEngine(session,api,local,checkpoint).sync();fail()}catch(_:IllegalArgumentException){}
        assertFalse(checkpoint.acknowledged(own))
    }
    @Test fun checkpointFailureAfterDurableRowsReplaysWithoutLosingOrResendingThem()=runTest {
        val api=Api();val local=Journal();val checkpoint=Checkpoint();val own=record()
        api.page=CloudPage(listOf(CloudStoredRecord(1,own.collection,own.record,1)),1,false,1);checkpoint.failCommit=true
        try{CloudSyncEngine(session,api,local,checkpoint).sync();fail()}catch(_:IllegalStateException){}
        assertEquals(listOf(own.copy(rosterRevision=1)),local.rows);assertEquals(0L,checkpoint.cursor())
        checkpoint.failCommit=false;CloudSyncEngine(session,api,local,checkpoint).sync()
        assertEquals(1,local.rows.size);assertEquals(1L,checkpoint.cursor());assertTrue(api.pushed.isEmpty())
    }
    @Test fun newerRosterIsSavedBeforeIncomingPageAndRejectedRecordDoesNotHoldOtherOwnRecords()=runTest {
        val api=Api();val local=Journal();val checkpoint=Checkpoint()
        val resident=record();val event=CloudRecord("events",buildJsonObject {put("groupId",group);put("eventId",UUID.randomUUID().toString());put("authorId",me)})
        local.rows+=listOf(resident,event);api.rejectKey=resident.key();api.page=CloudPage(emptyList(),0,false,2)
        val result=CloudSyncEngine(session,api,local,checkpoint).sync()
        assertEquals(2L,local.revision);assertEquals(2,api.rosterReads)
        assertEquals(1,result.rejected);assertEquals(1,result.uploaded);assertFalse(checkpoint.acknowledged(resident));assertTrue(checkpoint.acknowledged(event))
    }
}
