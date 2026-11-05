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

/** Downloads public reference maps only. Never deletes existing maps or changes group records. */
class CityOfflineMapTest {
 @Test fun cityDownloadPausesResumesAndRestoresWithOfflineAddresses()=runBlocking {
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext
  lateinit var maps:OfflineMapRepository
  inst.runOnMainSync {
   MapLibre.getInstance(app)
   org.maplibre.android.module.http.HttpRequestUtil.setOkHttpClient(okhttp3.OkHttpClient.Builder().dispatcher(okhttp3.Dispatcher().apply {maxRequests=4;maxRequestsPerHost=4}).addInterceptor {c->c.proceed(c.request().newBuilder().header("User-Agent","Tasukeai-Android-CityQA/1.5.41").build())}.build())
   maps=OfflineMapRepository(app);maps.selectCity("26100")
  }
  withTimeout(15_000){maps.state.first {!it.busy}}
  val started=System.currentTimeMillis()
  if(!maps.state.value.exists){
   inst.runOnMainSync {maps.downloadCity("26100");maps.pause()}
   withTimeout(20_000){maps.state.first {it.exists&&!it.busy}}
   inst.runOnMainSync {} ;assertFalse(maps.state.value.downloading)
  }
  inst.runOnMainSync {maps.resume()}
  val saved=withTimeout(480_000){maps.state.first {it.complete||it.error!=null}}
  assertNull(saved.error);assertTrue(saved.complete);assertTrue(saved.bytes>0);assertEquals("26100",saved.cityCode)
  android.util.Log.i("PlanD-CityQA","complete bytes=${saved.bytes} resources=${saved.resources} elapsedMs=${System.currentTimeMillis()-started}")
  lateinit var restored:OfflineMapRepository
  inst.runOnMainSync {restored=OfflineMapRepository(app);restored.selectCity("26100");MapLibre.setConnected(false)}
  try {
   val state=withTimeout(15_000){restored.state.first {!it.busy&&it.complete}};assertEquals(saved.bytes,state.bytes)
   val addresses=CityAddresses(app).search("26100","京都市中京区");assertFalse(addresses.isEmpty())
  }finally{inst.runOnMainSync {MapLibre.setConnected(null)}}
 }
}
