package io.github.nevaseme.plandmk1
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.buildings.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class AddressIntegrationTest {
 @Test fun packagedSearchHasHonestPrecisionAndWorksWithoutNetwork()=runBlocking<Unit>{
  val context=ApplicationProvider.getApplicationContext<Context>();val repo=CityAddresses(context);val begin=SystemClock.elapsedRealtime()
  val p=repo.candidates("左京区讃州寺町２０３","26100").single();assertEquals("block",p.precision);assertEquals("parcel",p.matchLevel);assertEquals("ISJ",p.source)
  assertEquals(135.774524,p.longitude,0.000001);Log.i("AddressIntegration","cold_ms="+(SystemClock.elapsedRealtime()-begin))
  assertTrue(repo.candidates("京都タワー","26100").any{it.precision=="poi"&&it.address=="京都タワー"})
  assertTrue(repo.candidates("六本木ヒルズ","13103").any{it.precision=="poi"&&it.address=="六本木ヒルズ"})
  assertTrue(repo.candidates("熱海市中央町1-1","22205").any{it.precision=="residential"})
  assertTrue(repo.candidates("熱海駅","26100").any{it.address=="熱海駅"})
  assertTrue(repo.candidates("京都市中京区寺町通御池上る上本能寺前町488番地","26100").any{it.address.endsWith("上本能寺前町488")})
  val samples=(0..39).map {val start=SystemClock.elapsedRealtime();repo.candidates(if(it%2==0)"左京区讃州寺町203" else "左京区讃州寺町99999","26100");SystemClock.elapsedRealtime()-start}.sorted()
  val metric="warm_p95_ms="+samples[37]+",max_ms="+samples.last();Log.i("AddressIntegration",metric);androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply{putString("stream","\n"+metric+"\n")});assertTrue("warm p95 ${samples[37]}ms",samples[37]<=500)
 }
 @Test fun homeSaveIsAtomicAndSurvivesRestart()=runBlocking<Unit>{
  val context=ApplicationProvider.getApplicationContext<Context>();val name="address-qa-${UUID.randomUUID()}.db";val id=UUID.randomUUID().toString()
  fun open()=Room.databaseBuilder(context,PlanDatabase::class.java,name).build()
  var db=open();try{var store=RoomRecordStore(db);val r=Resident("home-test",id,1,1,"架空",35.0,135.0,"2","東")
   store.updateOwnWithHome(r,BuildingLocationReference("試験町1"));val old=validHome(store.places(r.groupId),id)!!.buildingId
   store.updateOwnWithHome(r.copy(latitude=35.01,room=""),BuildingLocationReference("別町2"));assertNotEquals(old,validHome(store.places(r.groupId),id)!!.buildingId)
   db.close();db=open();store=RoomRecordStore(db);assertEquals(35.01,store.get(r.groupId,id)!!.latitude,0.0)
   val before=store.places(r.groupId);db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_home BEFORE INSERT ON place_events BEGIN SELECT RAISE(ABORT, 'test full'); END")
   try{store.updateOwnWithHome(r.copy(latitude=35.02),BuildingLocationReference("保存失敗"));fail("must rollback")}catch(_:android.database.sqlite.SQLiteException){}
   assertEquals(before,store.places(r.groupId));assertEquals(35.01,store.get(r.groupId,id)!!.latitude,0.0)
  }finally{db.close();context.deleteDatabase(name)}
 }
}
