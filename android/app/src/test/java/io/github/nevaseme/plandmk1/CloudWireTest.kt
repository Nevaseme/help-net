package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CloudWireTest {
    @Test fun cloudManagedHistoryUsesVersionEightAndLegacyRosterDoesNotAcknowledgeIt()=runTest {
        val me=UUID.randomUUID().toString();val report=QuakeEvent("trial",UUID.randomUUID().toString(),me,1,QuakeAction.REPORT,false,title="架空",area="試験")
        class Store(val revision:Long):RecordStore {
            var merged=0
            override suspend fun rosterRevision(groupId:String)=revision
            override suspend fun quakeEvents(groupId:String)=listOf(report)
            override suspend fun mergeQuake(event:QuakeEvent):Boolean {merged++;return true}
            override suspend fun all(groupId:String)=emptyList<Resident>()
            override suspend fun get(groupId:String,originId:String):Resident?=null
            override suspend fun merge(record:Resident)=false
        }
        val cloud=Store(1);val wire=mutableListOf<WireMessage>()
        SyncEngine("trial",me,cloud,{_,bytes->wire+=Json.decodeFromString<WireMessage>(bytes.decodeToString())}).connected("peer")
        assertEquals(8,wire.single {it.type=="QUAKE_SUMMARY"}.version)
        val legacy=Store(0);val returned=mutableListOf<WireMessage>();val engine=SyncEngine("trial",me,legacy,{_,bytes->returned+=Json.decodeFromString<WireMessage>(bytes.decodeToString())})
        engine.connected("peer");returned.clear()
        engine.receive("peer",Json.encodeToString(WireMessage(8,"trial","QUAKE_RECORD",quake=report)).encodeToByteArray())
        assertEquals(0,legacy.merged);assertTrue(returned.isEmpty())
    }
}
