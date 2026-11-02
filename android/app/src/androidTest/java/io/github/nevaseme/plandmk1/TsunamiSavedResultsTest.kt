package io.github.nevaseme.plandmk1

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.tsunami.TsunamiComputer
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real packaged regional results. No network, generated fixture, or substituted calculator. */
class TsunamiSavedResultsTest {
    @Test fun allThirtySixPackagedHeightsMatchSourceFieldsAndReportAdapterLatency() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val bytes=inst.targetContext.assets.open("tsunami-runtime/tsunami-precomputed-atami.json").use {it.readBytes()}
        val bundle=Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        assertEquals(36,bundle.getValue("cases").jsonArray.size)
        val grid=bundle.getValue("grid").jsonObject
        val bed=grid.getValue("bed").jsonArray.map {it.jsonPrimitive.double}
        val baseline=grid.getValue("baseline_m").jsonPrimitive.double
        val display=grid["display_cells"]?.jsonArray
        val cellArea=grid.getValue("dx").jsonPrimitive.double*grid.getValue("dy").jsonPrimitive.double
        val events=LinkedBlockingQueue<TsunamiComputer.Event>()
        fun terminal():TsunamiComputer.Event {
            val deadline=SystemClock.elapsedRealtime()+30_000
            while(SystemClock.elapsedRealtime()<deadline){val e=events.poll(1,TimeUnit.SECONDS)?:continue;if(e !is TsunamiComputer.Event.Progress)return e}
            throw AssertionError("Packaged saved result did not complete")
        }
        lateinit var computer:TsunamiComputer
        inst.runOnMainSync {computer=TsunamiComputer(inst.targetContext,{events.offer(it)})}
        val timings=mutableListOf<JsonObject>()
        try {
            assertEquals(TsunamiComputer.Event.Ready,terminal())
            // First load is measured separately; subsequent heights reuse the validated asset.
            for(height in listOf(35)+(0..35).toList()) {
                val started=SystemClock.elapsedRealtimeNanos()
                inst.runOnMainSync {computer.compute(height.toDouble())}
                val event=terminal();val milliseconds=(SystemClock.elapsedRealtimeNanos()-started)/1e6
                assertTrue("Height $height failed: $event",event is TsunamiComputer.Event.Result)
                val result=(event as TsunamiComputer.Event.Result).value
                val source=bundle.getValue("cases").jsonArray[height].jsonObject
                val depths=source.getValue("max_depth_m").jsonArray.map {it.jsonPrimitive.double}
                val indices=depths.indices.filter {bed[it]>=baseline&&depths[it]>=.1&&display?.get(it)?.jsonPrimitive?.boolean!=false}
                assertEquals(height.toDouble(),result.height,0.0)
                assertEquals(source.getValue("achieved_height_m").jsonPrimitive.double,result.achieved,1e-8)
                assertEquals(indices.size,Json.parseToJsonElement(result.features).jsonObject.getValue("features").jsonArray.size)
                assertEquals(indices.size*cellArea,result.area,1e-8)
                assertEquals(indices.maxOfOrNull {depths[it]}?:0.0,result.maximum,1e-8)
                timings+=buildJsonObject {put("height",height);put("phase",if(timings.isEmpty())"first-load" else "cached");put("adapter_ms",milliseconds);put("cells",indices.size)}
            }
            val evidence=buildJsonObject {
                put("passed",true);put("device_model",Build.MODEL);put("bundle_id",bundle.getValue("id"));put("real_packaged_data",true)
                put("sha256",MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
                put("measurement","Bundled asset to parsed native result; excludes map paint and touch latency")
                put("timings",JsonArray(timings))
            }
            File(inst.targetContext.getExternalFilesDir(null),"tsunami-saved-results.json").writeText(evidence.toString())
        } finally {inst.runOnMainSync {computer.close()}}
    }
}
