package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.eew.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class EewConnectionTest {
    private val device=UUID.randomUUID().toString()
    private fun packet(serial:Int=1,cancel:Boolean=false,training:Boolean=false,area:String="京都府南部",diagnostic:Boolean=false)=mapOf(
        "schema" to "pland-eew-1","source" to "wolfx","region" to "京都府南部","diagnostic" to diagnostic.toString(),
        "raw" to """{"type":"jma_eew","EventID":"20261030120000","Serial":$serial,"AnnouncedTime":"2026/10/30 12:00:00","Issue":{"Status":"${if(training) "訓練" else "通常"}"},"isTraining":$training,"isWarn":true,"isCancel":$cancel,"WarnArea":[{"Chiiki":"$area","Type":"警報"}]}""")
    private val issued=EewWarning.parse(packet().getValue("raw"),"wolfx")!!.issuedAt
    private fun withStore(block:(EewReceiptStore)->Unit){val dir=Files.createTempDirectory("eew-connection").toFile();try{block(EewReceiptStore(dir))}finally{dir.deleteRecursively()}}
    @Test fun failedDurableBackgroundEnqueueRetriesOriginalGroupWithoutRepeatingDatabaseSave()=withStore {store->
        var group="original";var saves=0;var attempts=0;var selections=0;val queued=mutableListOf<String>()
        val receiver=EewReceiver(store,{EewTarget(group,device)},{saves++;"incident"},{_,_->selections++},afterDurableSave={receipt->
            attempts++;if(attempts==1)error("work queue disk full");queued+=receipt.target!!.groupId
        })
        assertNotNull(receiver.receive(packet(),issued))
        assertTrue(store.pending(store.receipts().single()))
        group="changed";receiver.retryPending();receiver.retryPending()
        assertEquals(1,saves);assertEquals(2,attempts);assertEquals(listOf("original"),queued)
        assertEquals(1,selections);assertFalse(store.pending(store.receipts().single()))
        receiver.receive(packet(),issued+1);assertEquals(2,attempts)
    }
    @Test fun retryAfterSaveFailureKeepsOriginalGroupAndReceiptTime()=withStore {store->
        var group="first";var fail=true;val saved=mutableListOf<EewReceipt>();val selected=mutableListOf<String>()
        fun receiver()=EewReceiver(store,{EewTarget(group,device)},{r->if(fail)error("disk full");saved+=r;"incident"},{g,id->selected+="$g:$id"})
        assertThrows(IllegalStateException::class.java){receiver().receive(packet(),issued+10)}
        group="other";fail=false
        assertNotNull(receiver().receive(packet(),issued+900000))
        assertEquals("first",saved.single().target!!.groupId);assertEquals(issued+10,saved.single().receivedAt)
        assertEquals(listOf("first:incident"),selected);assertNull(receiver().receive(packet(),issued+900001));assertEquals(1,saved.size)
    }
    @Test fun selectionFailureRetriesSelectionWithoutResavingAndPendingSurvivesRestart()=withStore {store->
        var saves=0;var fail=true;var selected=0
        fun receiver()=EewReceiver(store,{EewTarget("trial",device)},{saves++;"incident"},{_,_->if(fail)error("selection disk full");selected++})
        assertThrows(IllegalStateException::class.java){receiver().receive(packet(),issued)}
        fail=false;receiver().retryPending();assertEquals(1,saves);assertEquals(1,selected)
        receiver().retryPending();assertEquals(1,selected)
    }
    @Test fun onlyFreshOrdinaryRegionalWarningsStartIncludingMissingIntensityAndBoundary()=withStore {store->
        val saved=mutableListOf<EewReceipt>();val r=EewReceiver(store,{EewTarget("trial",device)},{saved+=it;null},{_,_->})
        r.receive(packet(1),issued+180000)
        r.receive(packet(2),issued+180001)
        r.receive(packet(3,training=true),issued)
        r.receive(packet(4,area="京都府北部"),issued)
        r.receive(packet(5,diagnostic=true),issued,true)
        r.receive(packet(6,cancel=true),issued)
        r.receive(packet(1),issued)
        assertEquals(1,saved.size);assertEquals(-1,saved.single().warning.areas.single().scaleFrom)
        assertEquals(6,store.receipts().size)
    }
    @Test fun legacyHistoryAndUnregisteredReceiptsNeverReplayIntoANewGroup()=withStore {store->
        store.save(EewWarning.parse(packet().getValue("raw"),"wolfx")!!,issued)
        var starts=0;var target:EewTarget?=null
        val r=EewReceiver(store,{target},{starts++;null},{_,_->})
        r.retryPending();r.receive(packet(),issued)
        r.receive(packet(2),issued);target=EewTarget("new-group",device)
        r.retryPending();r.receive(packet(2),issued+1)
        assertEquals(0,starts)
    }
    @Test fun oneFailedPendingReceiptDoesNotStrandAnotherReceipt()=withStore {store->
        var bothFail=true;val selected=mutableListOf<String>()
        val r=EewReceiver(store,{EewTarget("trial",device)},{if(bothFail||it.warning.serial==1)error("unavailable");"second"},{_,i->selected+=i})
        for(serial in 1..2)assertThrows(IllegalStateException::class.java){r.receive(packet(serial),issued)}
        bothFail=false;assertThrows(IllegalStateException::class.java){r.retryPending()}
        assertEquals(listOf("second"),selected)
    }
}
