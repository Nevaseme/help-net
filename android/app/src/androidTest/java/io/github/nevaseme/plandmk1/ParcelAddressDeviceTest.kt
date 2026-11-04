package io.github.nevaseme.plandmk1

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.buildings.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Uses the actual bundled public parcel data without editing resident records. */
class ParcelAddressDeviceTest {
 @Test fun realParcelSearchAndBuildingCorrespondenceWorkOffline()=runBlocking<Unit> {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val addresses=CityAddresses(context)
  val buildings=BuildingAssessmentRepository {context.assets.open(it)}
  val parcels=ParcelAddressRepository {context.assets.open(it)}
  val samples=mutableListOf<JsonObject>()
  val queries=listOf("左京区讃州寺町203","左京区讃州寺町２０３","讃州寺町203","左京区讃州寺町999999","京都タワー","六本木ヒルズ")
  for(i in 0 until 31){
   val query=queries[i%queries.size];val begin=SystemClock.elapsedRealtimeNanos();val found=addresses.candidates(query,"26100");val ms=(SystemClock.elapsedRealtimeNanos()-begin)/1e6
   assertTrue(query,found.isNotEmpty())
   if(i%queries.size<3){val exact=found.first{it.address=="京都府京都市左京区讃州寺町203"};assertEquals("parcel-area",exact.precision);assertEquals(35.01152586,exact.latitude,0.0000001);assertEquals(135.77448339,exact.longitude,0.0000001)}
   if(i%queries.size==3)assertTrue(found.none{it.address.endsWith("999999")})
   samples+=buildJsonObject{put("query",query);put("ms",ms);put("count",found.size)}
  }
  val location=addresses.candidates(queries.first(),"26100").first{it.precision=="parcel-area"}
  val containing=buildings.atPoint(location.latitude,location.longitude,6.2).single().building
  assertEquals("bldg_eae1a120-90ce-4a16-83b8-9e4e2d33f17c",containing.id)
  val options=parcels.choices(containing.id,location.latitude,location.longitude)
  assertTrue(options.any{it.address==location.address})
  val sorted=samples.drop(6).map{it.getValue("ms").jsonPrimitive.double}.sorted()
  val p95=sorted[kotlin.math.ceil(sorted.size*.95).toInt()-1]
  assertTrue("Warm search p95 $p95 ms",p95<500)
  val result=buildJsonObject{put("passed",true);put("warmP95Ms",p95);put("buildingId",containing.id);put("samples",JsonArray(samples));put("candidateCount",options.size)}
  File(context.getExternalFilesDir(null),"parcel-address-test.json").writeText(result.toString())
 }
}
