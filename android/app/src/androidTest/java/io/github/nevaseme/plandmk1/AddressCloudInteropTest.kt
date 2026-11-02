package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Uses a disposable database and an explicitly supplied fictional group. */
class AddressCloudInteropTest {
 @Test fun homeProvenanceRoundTripsWithBrowser()=runBlocking<Unit>{
  val args=InstrumentationRegistry.getArguments();val endpoint=args.getString("cloudEndpoint").orEmpty();val invite=args.getString("browserInvite").orEmpty().split(':')
  assumeTrue(endpoint.isNotEmpty()&&invite.size==2)
  val context=ApplicationProvider.getApplicationContext<Context>();val name="address-cloud-${UUID.randomUUID()}";val device=UUID.randomUUID().toString();val group=invite[0]
  fun open()=Room.databaseBuilder(context,PlanDatabase::class.java,"$name.db").build()
  var db=open();val settings=CloudSettings(context,name);val api=CloudHttpApi()
  try{
   val registration=settings.submit(settings.begin(endpoint,device,"架空Android住所",groupId=group,code=invite[1]),api)
   val session=registration.session(endpoint);settings.complete(session)
   val journal=RoomCloudJournal(db,group,device);journal.updateRoster(registration.envelope(),device)
   val ref=AddressSearchReference("左京区讃州寺町203","京都市左京区讃州寺町203",35.011046,135.774524,"parcel","block","ISJ","24.0a")
   RoomRecordStore(db).updateOwnWithHome(Resident(group,device,1,System.currentTimeMillis(),"架空Android住所",35.01105,135.77453,"",""),BuildingLocationReference("京都市左京区讃州寺町203（確認）",searchReference=ref))
   suspend fun sync()=CloudSyncEngine(session,api,journal,settings.checkpoint(session)).sync()
   sync()
   withTimeout(120_000){while(true){sync();val rows=RoomRecordStore(db).places(group)
    if(placeBuildings(rows).any{it.authorId!=device&&it.locationReference?.address=="PWA確認済み住居"})break
    delay(1000)
   }}
   db.close();db=open();val rows=RoomRecordStore(db).places(group)
   val other=placeBuildings(rows).single{it.locationReference?.address=="PWA確認済み住居"}
   assertEquals("block",other.locationReference!!.searchReference!!.precision)
   assertEquals("parcel",other.locationReference!!.searchReference!!.matchLevel)
   assertEquals(35.0112,other.latitude!!,0.0000001)
   assertEquals(other.buildingId,validHome(rows,other.authorId)!!.buildingId)
   assertEquals(ref,placeBuildings(rows).single{it.authorId==device}.locationReference!!.searchReference)
  }finally{db.close();context.deleteDatabase("$name.db");context.getSharedPreferences(name,0).edit().clear().commit()}
 }
}
