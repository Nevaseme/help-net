package io.github.nevaseme.plandmk1

import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.maps.OfflineMapRepository
import io.github.nevaseme.plandmk1.buildings.CityAddresses
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.MapLibre

/** Public map packages only; no group, home, or participant records are changed. */
class VenueMapDeviceTest {
 @Test fun preparesBothDemoMapsAndRestoresToranomonOffline():Unit=runBlocking {
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as PlanDApplication
  lateinit var maps:OfflineMapRepository
  inst.runOnMainSync {
   MapLibre.getInstance(app)
   org.maplibre.android.module.http.HttpRequestUtil.setOkHttpClient(okhttp3.OkHttpClient.Builder().dispatcher(okhttp3.Dispatcher().apply{maxRequests=4;maxRequestsPerHost=4}).build())
   maps=OfflineMapRepository(app);maps.prepareCity("26100")
  }
  try {
   val kyoto=withTimeout(480_000){maps.state.first{it.cityCode=="26100"&&(it.complete||it.error!=null)}}
   assertNull(kyoto.error);assertTrue(kyoto.complete)
   inst.runOnMainSync {maps.prepareCity("13103")}
   val saved=withTimeout(480_000){maps.state.first{it.cityCode=="13103"&&(it.complete||it.error!=null)}}
   assertNull(saved.error);assertTrue(saved.complete)
   inst.runOnMainSync {maps.prepareCity("13103");maps.pause();MapLibre.setConnected(false)}
   lateinit var restored:OfflineMapRepository
   inst.runOnMainSync {restored=OfflineMapRepository(app);restored.prepareCity("13103")}
   val offline=withTimeout(20_000){restored.state.first{it.cityCode=="13103"&&!it.busy&&it.complete}}
   assertEquals(saved.bytes,offline.bytes)
   assertTrue(CityAddresses(app).search("13103","虎ノ門ヒルズ",includePlaces=true).isNotEmpty())
   assertTrue(app.buildingAssessments.atPoint(35.6668,139.7495,6.2).isNotEmpty())
   android.util.Log.i("PlanD-VenueMapQA","Kyoto bytes=${kyoto.bytes}; Minato bytes=${saved.bytes}; offline search+building passed")
  }finally{inst.runOnMainSync{maps.pause();MapLibre.setConnected(null)}}
 }
}
