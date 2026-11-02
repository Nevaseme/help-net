package io.github.nevaseme.plandmk1

import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.tsunami.TsunamiComputer
import org.junit.Assert.*
import org.junit.Test
import java.io.FileNotFoundException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Synthetic cases are injected only by this test, never packaged as real regional data. */
class TsunamiComputerTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private fun syntheticPack(): String {
        val hash = "a".repeat(64)
        val cases = (0..35).joinToString(",") { height ->
            val calibration = """{"target_height_m":$height,"achieved_height_m":$height,"residual_m":0,"tolerance_m":${if(height==0) .001 else .002},"forcing_amplitude_m":$height,"reference_index":0,"baseline_maximum_depth_drift_m":0}"""
            val diagnostics = """{"completed":true,"solver":"synthetic-test","simulated_s":600,"initial_volume_m3":1,"final_volume_m3":1,"net_boundary_volume_m3":0,"roundoff_volume_m3":0,"mass_residual_m3":0,"relative_mass_residual":0}"""
            val boundary = """{"inundation_reaches_closed_cut_boundary":false,"closed_cut_inundated_length_m":0,"supercritical_open_boundary":false}"""
            val proof = """{"domain_id":"synthetic-domain","source":{"domain_sha256":"$hash","solver_sha256":"$hash"},"achieved_height_m":$height,"reference_depth_m":${height+1},"calibration":$calibration,"diagnostics":$diagnostics,"boundary_audit":$boundary}"""
            """
            {"height_m":$height,"achieved_height_m":$height,"calibration":$calibration,
             "diagnostics":$diagnostics,
             "boundary_audit":$boundary,"source_proofs":{"primary":$proof,"secondary":$proof},
             "boundary_comparison":{"depthThresholdM":0.1,"meanWetDepthDifferenceM":0,"maximumDepthDifferenceM":0,"footprintIoU":1},
             "source_hashes":{"primary":"$hash","secondary":"$hash"},"max_depth_m":[${height / 10.0}]}
        """ }
        val contract = """{"id":"synthetic-domain","sha256":"$hash","duration_s":600,"tolerance_m":0.002,"maximum_forcing_m":35,"reference_index":0,"reference_bed_m":-1,"baseline_m":0}"""
        val source = """{"id":"synthetic-domain","sha256":"$hash"}"""
        return """{"schema":"pland-tsunami-precomputed-1","id":"synthetic-instrumentation-only","domain_id":"synthetic-test",
            "minimum_height_m":0,"maximum_height_m":35,"step_m":1,
            "grid":{"nx":1,"ny":1,"dx":25,"dy":25,"bed":[1],"baseline_m":0,"reference_index":null,"nodes_lonlat":[[139,35],[139.001,35],[139,35.001],[139.001,35.001]]},
            "validation_contracts":{"primary":$contract,"secondary":$contract},"source_domains":{"primary":$source,"secondary":$source},
            "reference_point":[139,35],"solver":{"version":"synthetic-test","implementation_sha256":"$hash"},
            "provenance":{"vertical_datum_status":"verified","source_sha256":"$hash"},"review":{"status":"experimental-checked"},"cases":[$cases]}"""
    }
    private fun terminal(events: LinkedBlockingQueue<TsunamiComputer.Event>): TsunamiComputer.Event {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            val event = events.poll(1, TimeUnit.SECONDS) ?: continue
            if (event !is TsunamiComputer.Event.Progress) return event
        }
        throw AssertionError("No terminal event from the packaged precomputed-result adapter")
    }

    @Test fun packagedAdapterSelectsExactCasesCancelsAndReleases() {
        val events = LinkedBlockingQueue<TsunamiComputer.Event>()
        lateinit var computer: TsunamiComputer
        val fixture = syntheticPack().toByteArray()
        inst.runOnMainSync {
            computer = TsunamiComputer(inst.targetContext, { events.offer(it) }) { path ->
                if (path.endsWith("tsunami-precomputed-atami.json")) fixture.inputStream() else inst.targetContext.assets.open(path)
            }
        }
        try {
            assertEquals(TsunamiComputer.Event.Ready, terminal(events))
            inst.runOnMainSync { computer.compute(35.0) }
            val first = terminal(events)
            assertTrue("Expected selected case, received $first", first is TsunamiComputer.Event.Result)
            assertEquals(35.0, (first as TsunamiComputer.Event.Result).value.height, 0.0)
            assertEquals(3.5, first.value.maximum, 0.0)
            inst.runOnMainSync { computer.compute(7.0); computer.cancel(); computer.compute(0.0) }
            val second = terminal(events)
            assertTrue("Expected latest exact case, received $second", second is TsunamiComputer.Event.Result)
            assertEquals(0.0, (second as TsunamiComputer.Event.Result).value.height, 0.0)
            inst.runOnMainSync { computer.compute(3.0); computer.close() }
            assertNull("Closed bridge must never publish a late result", events.poll(750, TimeUnit.MILLISECONDS))
        } finally { inst.runOnMainSync { computer.close() } }
    }

    @Test fun unreviewedBundleNeverPublishesResult() {
        val events = LinkedBlockingQueue<TsunamiComputer.Event>()
        lateinit var computer: TsunamiComputer
        val fixture = syntheticPack().replace("experimental-checked", "pending").toByteArray()
        inst.runOnMainSync {
            computer = TsunamiComputer(inst.targetContext, { events.offer(it) }) { path ->
                if (path.endsWith("tsunami-precomputed-atami.json")) fixture.inputStream() else inst.targetContext.assets.open(path)
            }
        }
        try {
            assertEquals(TsunamiComputer.Event.Ready, terminal(events))
            inst.runOnMainSync { computer.compute(3.0) }
            val event = terminal(events)
            assertTrue("Unreviewed bundle must fail closed: $event", event is TsunamiComputer.Event.Error)
            assertTrue((event as TsunamiComputer.Event.Error).message.contains("検証"))
        } finally { inst.runOnMainSync { computer.close() } }
    }

    @Test fun missingBundleNeverFallsBackToOldFixedResultOrLiveComputation() {
        val events = LinkedBlockingQueue<TsunamiComputer.Event>()
        lateinit var computer: TsunamiComputer
        inst.runOnMainSync {
            computer = TsunamiComputer(inst.targetContext, { events.offer(it) }) { path ->
                if (path.endsWith("tsunami-precomputed-atami.json")) throw FileNotFoundException("Test intentionally omits bundle")
                inst.targetContext.assets.open(path)
            }
        }
        try {
            assertEquals(TsunamiComputer.Event.Ready, terminal(events))
            inst.runOnMainSync { computer.compute(0.0) }
            val event = terminal(events)
            assertTrue("Missing cases must fail closed: $event", event is TsunamiComputer.Event.Error)
            assertTrue((event as TsunamiComputer.Event.Error).message.contains("取得できません"))
        } finally { inst.runOnMainSync { computer.close() } }
    }
}
