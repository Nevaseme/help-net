package io.github.nevaseme.plandmk1
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
class CommunityCloudDeviceTest {
 @Test fun publishedScenarioImportsIntoAndroidAndSharesInjury()=runBlocking<Unit>{
  val endpoint=InstrumentationRegistry.getArguments().getString("cloudEndpoint").orEmpty();assumeTrue(endpoint.isNotBlank());val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="community-cloud-${UUID.randomUUID()}";val settings=CloudSettings(context,name);val db=Room.databaseBuilder(context,PlanDatabase::class.java,name+".db").build();val api=CloudHttpApi()
  try{val pending=settings.begin(endpoint,UUID.randomUUID().toString(),"Demo organizer","Community verification ${UUID.randomUUID().toString().take(8)}");val admin=api.register(endpoint,pending.path,pending.body);val session=admin.session(endpoint);val envelope=api.startVenue(session,UUID.randomUUID().toString(),"CommunityDemo2026",positionMode="KYOTO");val venue=checkNotNull(envelope.venue);venue.validate(envelope.roster)
   val journal=RoomCloudJournal(db,admin.groupId,admin.deviceId);journal.updateRoster(envelope,admin.deviceId);var after=0L;do{val page=api.pull(session,after);journal.mergePage(page.records.map{it.value()});after=page.nextCursor}while(page.hasMore)
   val responses=ResponseStore(db){g,i,id->venue.active(g,i)&&venue.allPlacements().any{it.deviceId==id}};assertEquals(12,responses.events(admin.groupId).count{it.change is ResponseChange.Injury});assertEquals(3,responses.events(admin.groupId).count{it.change is ResponseChange.Report});assertEquals(13,venue.allPlacements().size)
   val person=venue.scenario!!.people.first();val old=responses.heads(admin.groupId).filter{(it.change as? ResponseChange.Injury)?.target?.id==person.deviceId};val event=responses.append(ResponseEvent(admin.groupId,UUID.randomUUID().toString(),admin.deviceId,1,System.currentTimeMillis(),venue.incidentId,ResponseChange.Injury(ResponseTarget(TargetKind.PERSON,person.deviceId),InjuryState.INJURED),old.map{it.eventId}));val row=journal.records().single{it.collection=="responses"&&it.key()==event.eventId};assertEquals(1,api.push(session,listOf(row)).acks.size)
   val legacy=okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(endpoint+"/api/v1/groups/"+admin.groupId+"/records?after=0&limit=100").header("Authorization","Bearer "+session.token).header("X-PlanD-Records-Version","5").build()).execute();legacy.use{assertEquals(426,it.code)}
  }finally{db.close();context.deleteDatabase(name+".db");context.getSharedPreferences(name,0).edit().clear().commit()}
 }
}
