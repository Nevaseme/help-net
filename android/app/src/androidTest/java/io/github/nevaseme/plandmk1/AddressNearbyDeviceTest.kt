package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import io.github.nevaseme.plandmk1.nearby.*
import io.github.nevaseme.plandmk1.sharing.SharingSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Two physical devices, real Nearby radio transport, disposable Room DB, no HTTP. */
class AddressNearbyDeviceTest {
 @Test fun offlineHomesCrossRealRadioAndSurviveReopen()=runBlocking<Unit>{
  val args=InstrumentationRegistry.getArguments();val group=args.getString("qaGroup").orEmpty();val side=args.getString("qaSide").orEmpty()
  assumeTrue(group.startsWith("address-radio-")&&side in listOf("A","B"))
  fun phase(message:String){android.util.Log.i("PlanD-RadioTest", "$side $message")}
  phase("entered")
  val context=ApplicationProvider.getApplicationContext<Context>();val id=if(side=="A")"10000000-0000-4000-8000-000000000001" else "20000000-0000-4000-8000-000000000002"
  val other=if(side=="A")"20000000-0000-4000-8000-000000000002" else "10000000-0000-4000-8000-000000000001"
  val name="$group-$side.db";fun open()=Room.databaseBuilder(context,PlanDatabase::class.java,name).build()
  var db=open();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);val port=NearbyTransport(context,scope);var session:SharingSession?=null
  try{
   phase("database created");val store=RoomRecordStore(db);val source=AddressSearchReference("左京区讃州寺町203","京都市左京区讃州寺町203",35.011046,135.774524,"parcel","block","ISJ","24.0a")
   val resident=Resident(group,id,1,System.currentTimeMillis(),"架空通信$side",35.011046,135.774524,"2","旧住戸")
   store.updateOwnWithHome(resident,BuildingLocationReference("通信$side-旧住居",searchReference=source))
   phase("initial home saved");withContext(Dispatchers.Main){port.configureAutomatic(GroupLink(group,id,"18".repeat(32)));session=SharingSession(group,id,port,store,scope,{phase("state $it")}, {},automatic=true);session!!.start("住所試験$side")}
   phase("session started");suspend fun await(label:String,condition:suspend ()->Boolean){withTimeout(120_000){while(!condition()){delay(500)}};phase(label)}
   await("initial") {validHome(store.places(group),other)!=null}
   store.updateOwnWithHome(resident.copy(latitude=35.012,longitude=135.775,floor="",room=""),BuildingLocationReference("通信$side-変更後",searchReference=source));withContext(Dispatchers.Main){session!!.changed()}
   await("updated") {placeBuildings(store.places(group)).any{it.authorId==other&&it.locationReference?.address=="通信${if(side=="A")"B" else "A"}-変更後"}&&store.get(group,other)?.latitude==35.012}
   val rows=store.places(group);val current=validHome(rows,other)!!;val building=placeBuildings(rows).single{it.buildingId==current.buildingId}
   assertEquals(source,building.locationReference!!.searchReference);assertNull(current.unitId);assertEquals("",store.get(group,other)!!.room)
   // Keep link alive briefly so the other device can consume our final update.
   delay(5000);withContext(Dispatchers.Main){session!!.stop()};db.close();db=open()
   val reopened=RoomRecordStore(db);assertEquals(current,validHome(reopened.places(group),other));assertEquals(35.012,reopened.get(group,other)!!.latitude,0.0)
  }finally{withContext(Dispatchers.Main){session?.stop();scope.cancel()};db.close();context.deleteDatabase(name)}
 }
}
