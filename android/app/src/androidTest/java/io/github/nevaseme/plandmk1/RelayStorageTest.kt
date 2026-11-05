package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import io.github.nevaseme.plandmk1.relay.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RelayStorageTest {
    @Test fun nearbyProofSurvivesRecipientRestartAndUploadsWithOriginalAuthor()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val group=UUID.randomUUID().toString();val a=UUID.randomUUID().toString();val b=UUID.randomUUID().toString()
        val names=listOf("relay-a-$group.db","relay-b-$group.db")
        val dbA=Room.databaseBuilder(context,PlanDatabase::class.java,names[0]).build()
        fun openB()=Room.databaseBuilder(context,PlanDatabase::class.java,names[1]).build()
        var dbB=openB()
        try {
            val profile=Resident(group,a,1,1,"Fictional author",35.0,135.0,"1","test",Safety.SAFE)
            RoomRecordStore(dbA,a).merge(profile)
            val signer=AndroidRelaySigner()
            val repoA=RoomRelayRepository(dbA,group,a,signer)
            val repoB=RoomRelayRepository(dbB,group,b,signer)
            val queue=ArrayDeque<Triple<Boolean,String,ByteArray>>()
            val relayA=RelayExchange(group,repoA,{peer,bytes->queue.add(Triple(true,peer,bytes))})
            val relayB=RelayExchange(group,repoB,{peer,bytes->queue.add(Triple(false,peer,bytes))})
            val engineA=SyncEngine(group,a,RoomRecordStore(dbA,a),{peer,bytes->queue.add(Triple(true,peer,bytes))},relay=relayA)
            val engineB=SyncEngine(group,b,RoomRecordStore(dbB,b),{peer,bytes->queue.add(Triple(false,peer,bytes))},relay=relayB)
            engineA.connected("b");engineB.connected("a")
            var messages=0
            while(queue.isNotEmpty()) {
                check(++messages<100)
                val (fromA,_,bytes)=queue.removeFirst();assertTrue(bytes.size<=24000)
                if(fromA)engineB.receive("a",bytes) else engineA.receive("b",bytes)
            }
            assertEquals(profile,RoomRecordStore(dbB,b).get(group,a))
            val outgoing=RoomCloudJournal(dbB,group,b).records().single()
            assertNotNull(outgoing.relayProof)
            val proofBody=outgoing.relayProof!!.body
            dbB.close();dbB=openB()
            val reopened=RoomCloudJournal(dbB,group,b).records().single()
            assertEquals(proofBody,reopened.relayProof!!.body)
            assertEquals(a,reopened.owner())
            val raw=repoA.envelopes().single()
            try {RoomRelayRepository(dbB,group,b,signer).accept(raw.copy(proof=raw.proof.copy(body=raw.proof.body.replace("Fictional author","Tampered author"))));fail("tampered signature")}catch(_:IllegalArgumentException){}
            assertEquals(profile,RoomRecordStore(dbB,b).get(group,a))
        }finally {dbA.close();dbB.close();names.forEach {context.deleteDatabase(it)}}
    }
}
