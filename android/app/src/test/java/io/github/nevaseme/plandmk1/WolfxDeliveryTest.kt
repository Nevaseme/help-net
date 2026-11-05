package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.eew.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class WolfxDeliveryTest {
    private val issued = 1792897200000L // 2026-10-25 12:00 JST
    private fun packet(serial: Int = 1, cancel: Boolean = false, training: Boolean = false, area: Boolean = true, intensity: Boolean = false) = mapOf(
        "schema" to "pland-eew-1", "source" to "wolfx", "region" to "京都府南部", "diagnostic" to training.toString(),
        "raw" to """{"type":"jma_eew","EventID":"20261025120000","Serial":$serial,"AnnouncedTime":"2026/10/25 12:00:00","Issue":{"Status":"${if(training) "訓練" else "通常"}"},"isTraining":$training,"isWarn":true,"isCancel":$cancel,"WarnArea":[${if(area) """{"Chiiki":"京都府南部","Type":"警報"${if(intensity) ""","Shindo1":"6弱","Shindo2":"5弱"""" else ""}}""" else ""}]}"""
    )
    private fun withReceiver(block: (EewReceiver, EewReceiptStore) -> Unit) {
        val dir = Files.createTempDirectory("pland-wolfx").toFile()
        try { val store = EewReceiptStore(dir); block(EewReceiver(store), store) } finally { dir.deleteRecursively() }
    }
    @Test fun savesWolfxWithoutIntensityAndRestoresItsSource() = withReceiver { receiver, store ->
        val notice = requireNotNull(receiver.receive(packet(), issued + 1000))
        assertTrue(notice.body.contains("震度不明")); assertTrue(notice.body.contains("Wolfx"))
        assertEquals("wolfx", store.receipts().single().warning.source)
        assertEquals(packet().getValue("raw"), store.receipts().single().warning.raw)
        assertNull(receiver.receive(packet(), issued + 2000))
    }
    @Test fun staleIsSavedButNotNotifiedAndThreeMinuteBoundaryIsIncluded() = withReceiver { receiver, store ->
        assertNotNull(receiver.receive(packet(), issued + 180000))
        assertNull(receiver.receive(packet(serial = 2), issued + 180001))
        assertEquals(2, store.receipts().size)
    }
    @Test fun parsesRangesAndDoesNotTreatNationwideMaximumAsLocalIntensity() = withReceiver { receiver, _ ->
        assertTrue(receiver.receive(packet(intensity = true), issued + 1)!!.body.contains("5弱〜6弱"))
        val data = packet(serial = 2).toMutableMap()
        data["raw"] = data.getValue("raw").replace("\"Serial\":2", "\"Serial\":2,\"MaxIntensity\":\"7\"")
        assertTrue(receiver.receive(data, issued + 2)!!.body.contains("震度不明"))
    }
    @Test fun cancellationAndOutOfOrderAreKeptSeparateFromTraining() = withReceiver { receiver, store ->
        val first = receiver.receive(packet(), issued + 1)!!
        val cancel = receiver.receive(packet(serial = 3, cancel = true, area = false), issued + 2)!!
        assertEquals(first.tag, cancel.tag); assertTrue(cancel.title.contains("取消"))
        assertNull(receiver.receive(packet(serial = 2), issued + 3))
        assertNull(receiver.receive(packet(serial = 4, training = true), issued + 4))
        assertEquals("Help Net 受信試験", receiver.receive(packet(serial = 5, training = true), issued + 5, true)!!.title)
        assertTrue(store.receipts().any { it.warning.cancelled })
    }
    @Test fun sourceMismatchCannotMakeAHeartbeatOrP2pPacketIntoWolfx() = withReceiver { receiver, store ->
        assertNull(receiver.receive(packet() + ("raw" to """{"type":"heartbeat"}"""), issued))
        assertNull(receiver.receive(packet() + ("source" to "p2pquake"), issued))
        assertTrue(store.receipts().isEmpty())
    }
    @Test fun aPhoneClockOneSecondBehindDoesNotSuppressAnImmediateDelivery() = withReceiver { receiver, store ->
        assertNotNull(receiver.receive(packet(), issued - 1000))
        assertEquals(issued - 1000, store.receipts().single().receivedAt)
    }
}
