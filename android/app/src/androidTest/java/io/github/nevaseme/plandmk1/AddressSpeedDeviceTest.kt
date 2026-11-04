package io.github.nevaseme.plandmk1

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.buildings.CityAddresses
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Measures the real bundled search without modifying resident or group records. */
class AddressSpeedDeviceTest {
 @Test fun coldAndRepeatedSearch()=runBlocking<Unit> {
  val repo=CityAddresses(InstrumentationRegistry.getInstrumentation().targetContext)
  val queries=listOf("左京区讃州寺町203","左京区讃州寺町２０３","讃州寺町203","京都タワー","六本木ヒルズ","左京区讃州寺町999999")
  val times=mutableListOf<Double>()
  for(i in 0 until 25){val q=queries[if(i==0)0 else (i-1)%queries.size];val start=SystemClock.elapsedRealtimeNanos();val result=repo.candidates(q,"26100");val ms=(SystemClock.elapsedRealtimeNanos()-start)/1e6
   assertTrue("No result: $q",result.isNotEmpty())
   if(q.contains("203")||q.contains("２０３")){assertEquals("parcel-area",result.first().precision);assertTrue(result.first().address.contains("讃州寺町203"))}
   if(i>0)times+=ms
   Log.i("AddressSpeed","sample=$i ms=$ms hits=${result.size} query=$q")
  }
  val sorted=times.sorted();Log.i("AddressSpeed","warm median=${sorted[sorted.size/2]} p95=${sorted[kotlin.math.ceil(sorted.size*.95).toInt()-1]} ms")
 }
}
