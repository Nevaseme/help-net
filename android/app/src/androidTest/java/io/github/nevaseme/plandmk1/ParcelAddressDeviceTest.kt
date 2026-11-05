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
 @Test fun addressesAcrossAllElevenWardsUseTheSameSearch()=runBlocking<Unit> {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val repo=CityAddresses(context)
  val index=Json.parseToJsonElement(context.assets.open("kyoto-parcel-search/index.json").bufferedReader().use{it.readText()}).jsonObject
  val samples=linkedMapOf<String,LinkedHashMap<String,String>>()
  for(chunk in index.getValue("chunks").jsonArray){
   val file=chunk.jsonObject.getValue("file").jsonPrimitive.content
   val data=java.util.zip.GZIPInputStream(context.assets.open("kyoto-parcel-search/$file")).bufferedReader().use{Json.parseToJsonElement(it.readText()).jsonObject}
   for(raw in data.getValue("rows").jsonArray){
    val row=raw.jsonArray;val address=row[1].jsonPrimitive.content
    val ward=row[5].jsonObject.getValue("wardCode").jsonPrimitive.content
    val town=address.replace(Regex("[0-9].*$"),"")
    val selected=samples.getOrPut(ward){linkedMapOf()}
    if(selected.size<3&&!selected.containsKey(town)&&address.any{it in '0'..'9'})selected[town]=address
   }
   if(samples.size==11&&samples.values.all{it.size==3})break
  }
  assertEquals("All Kyoto wards",11,samples.size)
  assertTrue(samples.values.all{it.size==3})
  val measurements=mutableListOf<JsonObject>()
  for((ward,towns) in samples)for((town,address) in towns){
   val fullWidth=address.map{if(it in '0'..'9')('０'.code+it.code-'0'.code).toChar() else it}.joinToString("")
   for(query in listOf(address,address.removePrefix("京都府京都市"),fullWidth)){
    val begin=SystemClock.elapsedRealtimeNanos();val found=repo.candidates(query,"26100")
    val exact=found.firstOrNull{it.address==address}
    assertNotNull("No exact address for $query",exact)
    assertFalse("Coarse match presented as exact for $query",exact!!.precision in listOf("town","block","area"))
    measurements+=buildJsonObject{put("ward",ward);put("query",query);put("precision",exact.precision);put("ms",(SystemClock.elapsedRealtimeNanos()-begin)/1e6)}
   }
   val missing=town+"999999999999"
   assertTrue("Invented parcel $missing",repo.candidates(missing,"26100").none{it.address==missing})
  }
  File(context.getExternalFilesDir(null),"parcel-wards-test.json").writeText(buildJsonObject{put("passed",true);put("wards",samples.size);put("addresses",samples.values.sumOf{it.size});put("exactQueries",measurements.size);put("missingNumberQueries",33);put("samples",JsonArray(measurements))}.toString())
 }

}
