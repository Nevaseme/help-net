package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.eew.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EewDeliveryTest {
    private fun packet(id: String = "one", serial: Int = 1, cancelled: Boolean = false, test: Boolean = false,
                       area: Boolean = true, diagnostic: Boolean = false) = mapOf(
        "schema" to "pland-eew-1", "source" to "p2pquake", "region" to "京都府南部",
        "diagnostic" to diagnostic.toString(),
        "raw" to """{"id":"$id","code":556,"test":$test,"issue":{"eventId":"20261025120000","serial":"$serial","time":"2026/10/25 12:00:00"},"cancelled":$cancelled,"earthquake":{},"areas":[${if(area) """{"pref":"京都府","name":"京都府南部","scaleFrom":45,"scaleTo":55,"kindCode":"19"}""" else ""}]}"""
    )
    private fun withReceiver(block: (EewReceiver, EewReceiptStore) -> Unit) {
        val dir = Files.createTempDirectory("pland-delivery").toFile()
        try { val store = EewReceiptStore(dir); block(EewReceiver(store), store) } finally { dir.deleteRecursively() }
    }

    @Test fun savesBeforeReturningARegionalNoticeWithIntensityRange() = withReceiver { receiver, store ->
        val notice = requireNotNull(receiver.receive(packet(), 100))
        assertEquals(1, store.receipts().size)
        assertEquals("緊急地震速報（警報）", notice.title)
        assertTrue(notice.body.contains("京都府南部"))
        assertTrue(notice.body.contains("5弱〜6弱"))
        assertEquals("eew:false:20261025120000", notice.tag)
        assertNull(receiver.receive(packet(), 200))
    }
    @Test fun cancellationUpdatesTheSameNoticeAndLateReportsCannotRestoreIt() = withReceiver { receiver, store ->
        val first = receiver.receive(packet(), 100)!!
        val cancelled = receiver.receive(packet("cancel", 3, cancelled = true, area = false), 200)!!
        assertEquals(first.tag, cancelled.tag)
        assertTrue(cancelled.title.contains("取消"))
        assertNull(receiver.receive(packet("late", 2), 300))
        assertTrue(store.latest("20261025120000")!!.warning.cancelled)
    }
    @Test fun scopeRemovalDoesNotContinueDisplayingAWarning() = withReceiver { receiver, _ ->
        receiver.receive(packet(), 100)
        val changed = receiver.receive(packet("changed", 2, area = false), 200)!!
        assertTrue(changed.body.contains("対象から外れました"))
    }
    @Test fun testTrafficNeedsExplicitDiagnosticAndDebugPermission() = withReceiver { receiver, _ ->
        assertNull(receiver.receive(packet(test = true), 100, allowDiagnostics = true))
        assertNull(receiver.receive(packet("prod-test", test = true, diagnostic = true), 200))
        val test = receiver.receive(packet("debug-test", serial = 2, test = true, diagnostic = true), 300, allowDiagnostics = true)!!
        assertEquals("Help Net 受信試験", test.title)
        assertTrue(test.tag.startsWith("eew:true:"))
    }
    @Test fun ignoresForeignEnvelopesAndBrokenPayloads() = withReceiver { receiver, store ->
        assertNull(receiver.receive(packet() + ("source" to "other"), 100))
        assertNull(receiver.receive(packet() + ("region" to "京都府北部"), 100))
        assertNull(receiver.receive(packet() + ("raw" to "not json"), 100))
        assertTrue(store.receipts().isEmpty())
    }
    @Test fun unknownIntensityIsNotConvertedIntoAnExactNumber() = withReceiver { receiver, _ ->
        val data = packet().toMutableMap()
        data["raw"] = data.getValue("raw").replace("\"scaleFrom\":45", "\"scaleFrom\":-1").replace("\"scaleTo\":55", "\"scaleTo\":99")
        assertTrue(receiver.receive(data, 100)!!.body.contains("震度不明"))
    }
}
