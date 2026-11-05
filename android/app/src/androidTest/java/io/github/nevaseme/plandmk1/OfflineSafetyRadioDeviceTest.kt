package io.github.nevaseme.plandmk1

import android.app.NotificationManager
import android.os.PowerManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.CloudSettings
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import io.github.nevaseme.plandmk1.sharing.SharingService
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real production service/radio and Room. User controls screens and Bluetooth.
 * Temporarily selects an isolated fixture group; restores the previous selection.
 * Never invokes radio/permission settings or writes real safety/report records. */
class OfflineSafetyRadioDeviceTest {
 private val inst get()=InstrumentationRegistry.getInstrumentation()
 private val app get()=inst.targetContext.applicationContext as PlanDApplication
 private val status get()=File(app.filesDir,"qa-safety-status.json")
 private val control get()=File(app.filesDir,"qa-safety-control.txt")
 private val backup get()=File(app.filesDir,"qa-safety-backup.json")
 private fun bluetooth()=app.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled==true
 private fun wifi()=(app.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager).isWifiEnabled
 private fun now()=System.currentTimeMillis()
 private fun phase(name:String,extra:String="") {
  val body=JSONObject().put("phase",name).put("group",InstrumentationRegistry.getArguments().getString("qaGroup")).put("at",now()).put("screenOn",app.getSystemService(PowerManager::class.java).isInteractive).put("connected",app.sharing.state.value.connected.size).put("bluetooth",bluetooth()).put("wifi",wifi()).put("sharingActive",app.sharing.state.value.active).put("error",app.sharing.state.value.error).put("extra",extra)
  status.writeText(body.toString());android.util.Log.i("PlanD-SafetyRadio",body.toString())
 }
 private suspend fun restore() {
  if(!backup.exists())return
  val b=JSONObject(backup.readText())
  withContext(Dispatchers.Main){app.sharing.stop();app.installation.setGroup(b.getString("group"))}
  val prefs=app.getSharedPreferences("nearby-auto",0).edit()
  if(b.isNull("automaticGroup"))prefs.remove("group") else prefs.putString("group",b.getString("automaticGroup"))
  check(prefs.commit());backup.delete()
 }
 @Test fun safetyReportsScreenOffAndAutomaticReconnect()=runBlocking<Unit>{
  val args=InstrumentationRegistry.getArguments()
  if(args.getString("qaCleanup")=="true"){restore();phase("restored");return@runBlocking}
  val group=args.getString("qaGroup").orEmpty();val side=args.getString("qaSide").orEmpty();val peer=args.getString("qaPeer").orEmpty()
  require(group.startsWith("safety-radio-")&&group.length<=40&&side in listOf("A","B")&&peer.length==36)
  check(!backup.exists()){ "Previous test requires cleanup before starting" }
  val original=app.installation.group.value
  val originalPositionFactory=app.positionSourceFactory
  backup.writeText(JSONObject().put("group",original).put("testGroup",group).put("peer",peer).put("side",side).put("automaticGroup",app.getSharedPreferences("nearby-auto",0).getString("group",null)?:JSONObject.NULL).toString())
  control.writeText("");val own=app.installation.localId;val admin=if(side=="A")own else peer
  val incident=UUID.nameUUIDFromBytes("$group-incident".toByteArray()).toString()
  val roster=MemberRoster(groupId=group,members=listOf(GroupMember(admin,"通信確認A",MemberRole.ADMIN,listOf(admin)),GroupMember(if(side=="B")own else peer,"通信確認B",MemberRole.MEMBER,listOf(if(side=="B")own else peer))))
  val quakes=QuakeStore(app.database);val responses=ResponseStore(app.database);val name="通信確認$side"
  suspend fun await(label:String,timeout:Long=180_000,condition:suspend()->Boolean){withTimeout(timeout){while(!condition()){check(control.readText().trim()!="abort"){"Trial aborted by controller"};delay(250)}};phase(label)}
  suspend fun command(value:String) {
   while(control.readText().trim()!=value) {check(control.readText().trim()!="abort"){"Trial aborted by controller"};delay(250)}
   phase("command-$value")
  }
  suspend fun publish(stage:Int,safety:Safety){
   app.store.answerWithMemo(IncidentEvent(group,UUID.randomUUID().toString(),incident,own,name,1,now(),IncidentKind.ANSWER,safety=safety),if(safety==Safety.SOS)"架空の通信試験SOS" else "")
   val id=UUID.randomUUID().toString()
   responses.append(ResponseEvent(group,id,own,1,now(),incident,ResponseChange.Report(id,"道路",35.011,135.774,"通信確認$side/$stage")))
   withContext(Dispatchers.Main){app.sharing.changed()}
  }
  suspend fun received(stage:Int,safety:Safety):Boolean {
   val answer=incidentView(app.store.incidents(group),incident)?.answers?.get(peer)
   return answer?.safety==safety && responses.events(group).any{it.actorDeviceId==peer&&(it.change as? ResponseChange.Report)?.description=="通信確認${if(side=="A")"B" else "A"}/$stage"}
  }
  fun screenOff()=!app.getSystemService(PowerManager::class.java).isInteractive
  try {
   app.positionSourceFactory={ {io.github.nevaseme.plandmk1.location.PositionFix(35.011,135.774,10.0,now())} }
   withContext(Dispatchers.Main){app.sharing.stop();app.installation.setGroup(group)}
   CloudSettings(app).saveNearbyKey(group,"27".repeat(32))
   quakes.importRoster(roster.encode(),own)
   quakes.command(QuakeEvent(group,incident,admin,1,QuakeAction.TRAINING_START,true,title="架空の通信試験",area="試験専用"))
   app.store.updateOwn(group,own){Resident(group,own,1,now(),name,35.011,135.774,"","")}
   publish(1,Safety.SAFE)
   phase("prepared")
   await("foreground"){app.sharing.foreground}
   withContext(Dispatchers.Main){app.sharing.start(group,name)}
   await("connected"){app.sharing.state.value.connected.isNotEmpty()}
   await("initial-pass"){received(1,Safety.SAFE)}
   if(args.getString("qaWifi")=="true") {
    command("wifi")
    await("wifi-only-ready"){!bluetooth()&&wifi()}
    publish(10,Safety.SOS)
    while(control.readText().trim()!="wifi-end") {
     check(control.readText().trim()!="abort"){"Trial aborted by controller"}
     phase("wifi-observation", "received="+received(10,Safety.SOS));delay(2_000)
    }
    phase("wifi-result", "received="+received(10,Safety.SOS))
   }
   command("screen");await("screen-off"){screenOff()};delay(15_000)
   publish(2,Safety.SOS);await("screen-received"){received(2,Safety.SOS)}
   assertTrue("Reception must occur with screen off",screenOff())
   assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.any {it.id==SharingService.NOTIFICATION_ID})
   phase("screen-pass")
   command("disconnect");await("disconnected"){app.sharing.state.value.connected.isEmpty()}
   publish(3,Safety.SAFE);delay(5_000);assertFalse("Queued report must not cross a disconnected link",received(3,Safety.SAFE));phase("queued")
   command("reconnect");await("reconnected"){app.sharing.state.value.connected.isNotEmpty()}
   await("reconnect-received"){received(3,Safety.SAFE)}
   val reader=Room.databaseBuilder(app,PlanDatabase::class.java,"pland.db").build()
   try{assertEquals(Safety.SAFE,incidentView(RoomRecordStore(reader).incidents(group),incident)?.answers?.get(peer)?.safety);assertTrue(ResponseStore(reader).events(group).any{it.actorDeviceId==peer&&(it.change as? ResponseChange.Report)?.description?.endsWith("/3")==true})}finally{reader.close()}
   phase("all-pass")
   if(args.getString("qaBoot")=="true") {
    command("reboot-disconnect")
    await("reboot-disconnected"){!bluetooth()&&!wifi()&&app.sharing.state.value.connected.isEmpty()}
    publish(4,Safety.SOS);delay(5_000)
    assertFalse("Pre-reboot report must still be queued", received(4,Safety.SOS))
    // The PC reads Room and foreground-service evidence after an actual OS reboot,
    // before running any further instrumentation or opening the app.
    phase("reboot-queued")
   }
   command("finish")
  }catch(e:Exception){phase("failed",e.toString());throw e}
  finally{withContext(NonCancellable){restore();app.positionSourceFactory=originalPositionFactory}}
 }
}
