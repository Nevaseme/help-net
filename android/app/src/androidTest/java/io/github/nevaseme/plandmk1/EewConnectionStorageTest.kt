package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import io.github.nevaseme.plandmk1.eew.*
import io.github.nevaseme.plandmk1.ui.MainDestination
import io.github.nevaseme.plandmk1.ui.mainDestination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Independent databases, receipt files, profiles and selection; never touches the installed user's group. */
class EewConnectionStorageTest {
    private fun id()=UUID.randomUUID().toString()
    private val group="isolated-eew"
    private fun packet(serial:Int=1,cancel:Boolean=false,area:String="京都府南部")=mapOf("schema" to "pland-eew-1","source" to "wolfx","region" to "京都府南部",
        "raw" to """{"type":"jma_eew","EventID":"20261030120000","Serial":$serial,"AnnouncedTime":"2026/10/30 12:00:00","Issue":{"Status":"通常"},"isTraining":false,"isWarn":true,"isCancel":$cancel,"WarnArea":[{"Chiiki":"$area","Type":"警報"}]}""")
    private val issued=EewWarning.parse(packet().getValue("raw"),"wolfx")!!.issuedAt
    private fun roster(admin:String,member:String)=MemberRoster(groupId=group,members=listOf(GroupMember(admin,"試験管理者",MemberRole.ADMIN,listOf(admin)),GroupMember(member,"試験受信者",MemberRole.MEMBER,listOf(member))))

    @Test fun backgroundReceiptReopenFirstAnswerMapAndAdminEndPreserveAnswers()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="eew-${id()}.db";val directory=File(context.cacheDir,"eew-${id()}")
        val admin=id();val member=id();val roster=roster(admin,member);val prefs=context.getSharedPreferences("eew-selection-${id()}",0)
        fun open()=Room.databaseBuilder(context,PlanDatabase::class.java,name).build()
        var db=open()
        fun receiver()=EewReceiver(EewReceiptStore(directory),{EewTarget(group,member)},{runBlocking {QuakeStore(db).receiveEew(it)}},{g,incident->check(prefs.edit().putString(g,incident).commit())})
        suspend fun projection():IncidentUiState {val s=RoomRecordStore(db);val q=QuakeStore(db);val events=q.events(group);return projectIncidentUi(group,s.all(group),s.events(group),s.incidents(group),canonicalIncidentId(roster,events,prefs.getString(group,null)),managedIncidentOptions(s.incidents(group),roster,events))}
        try {
            QuakeStore(db).importRoster(roster.encode(),member)
            RoomRecordStore(db).merge(Resident(group,member,1,1,"架空受信者",35.0,135.7,"1階","試験",Safety.UNREPORTED))
            assertNotNull(receiver().receive(packet(),issued+1))
            db.close();db=open();receiver().retryPending()
            val initial=projection();val incident=initial.selection
            assertTrue(initial.canEdit);assertEquals(MainDestination.ANSWER,mainDestination(initial.canEdit,initial.current!!.answers.containsKey(member)))
            assertEquals(0,QuakeStore(db).events(group).count {it.action==QuakeAction.REPORT})
            assertFalse(roster.memberForDevice(member).role==MemberRole.ADMIN)
            val answer=IncidentEvent(group,id(),incident,member,"架空受信者",1,issued+10,IncidentKind.ANSWER,training=false,safety=Safety.SAFE)
            RoomRecordStore(db).appendIncident(answer)
            assertEquals(MainDestination.MAP,projection().let {mainDestination(it.canEdit,it.current!!.answers.containsKey(member))})
            // A later serial and cancellation retain the answer and never override a deliberate selection.
            prefs.edit().putString(group,"legacy").commit()
            receiver().receive(packet(2),issued+20);receiver().receive(packet(3,cancel=true),issued+30)
            assertEquals("legacy",prefs.getString(group,null))
            val state=QuakeStore(db).states(group).single();assertFalse(state.closed)
            assertEquals(answer.eventId,managedIncidentOptions(RoomRecordStore(db).incidents(group),roster,QuakeStore(db).events(group)).single().answers[member]!!.eventId)
            try {QuakeStore(db).command(QuakeEvent(group,id(),member,issued+40,QuakeAction.END,false,incident));fail("non-admin end")}catch(_:IllegalArgumentException){}
            QuakeStore(db).command(QuakeEvent(group,id(),admin,issued+40,QuakeAction.END,false,incident))
            receiver().receive(packet(4),issued+50)
            assertTrue(QuakeStore(db).states(group).single().closed)
            assertEquals(1,RoomRecordStore(db).incidents(group).count {it.kind==IncidentKind.ANSWER})
        }finally {db.close();context.deleteDatabase(name);directory.deleteRecursively();prefs.edit().clear().commit()}
    }

    @Test fun crashAfterRoomCommitRepairsSelectionAndMissingRosterDoesNotJoinLater()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="eew-${id()}.db";val directory=File(context.cacheDir,"eew-${id()}")
        val admin=id();val member=id();val db=Room.databaseBuilder(context,PlanDatabase::class.java,name).build();var selected:String?=null;var failAfterCommit=true
        fun receiver()=EewReceiver(EewReceiptStore(directory),{EewTarget(group,member)},{receipt->runBlocking {QuakeStore(db).receiveEew(receipt)}.also {if(failAfterCommit)error("interrupted after transaction") }},{_,i->selected=i})
        try {
            QuakeStore(db).importRoster(roster(admin,member).encode(),member)
            try {receiver().receive(packet(),issued);fail("interruption")}catch(_:IllegalStateException){}
            assertNull(selected);assertEquals(1,QuakeStore(db).states(group).size)
            failAfterCommit=false;receiver().retryPending()
            assertEquals(QuakeStore(db).states(group).single().formation.incidentId,selected)
            assertEquals(1,QuakeStore(db).events(group).size)
            val unknown="unknown";val unknownDir=File(directory,"unregistered")
            val unregistered=EewReceiver(EewReceiptStore(unknownDir),{EewTarget(unknown,member)},{runBlocking {QuakeStore(db).receiveEew(it)}},{_,_->fail("unregistered selection")})
            unregistered.receive(packet(),issued)
            QuakeStore(db).importRoster(roster(admin,member).copy(groupId=unknown).encode(),member)
            unregistered.retryPending();assertTrue(QuakeStore(db).events(unknown).isEmpty())
        }finally {db.close();context.deleteDatabase(name);directory.deleteRecursively()}
    }

    @Test fun distinctSerialDevicesSyncEndBeforeProofWithoutReopening()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val names=List(2){"eew-sync-${id()}.db"};val dbs=names.map {Room.databaseBuilder(context,PlanDatabase::class.java,it).build()}
        val admin=id();val member=id();val roster=roster(admin,member);val devices=listOf(admin,member);val queue=ArrayDeque<Triple<Int,Int,ByteArray>>();val errors=mutableListOf<String>()
        val engines=dbs.mapIndexed {i,db->SyncEngine(group,devices[i],RoomRecordStore(db,devices[i]),{to,data->queue.add(Triple(i,to.toInt(),data))},errors::add)}
        try {
            for(i in dbs.indices){val q=QuakeStore(dbs[i]);q.importRoster(roster.encode(),devices[i]);q.receiveEew(EewReceipt(EewWarning.parse(packet(i+1).getValue("raw"),"wolfx")!!,issued+if(i==0)1 else 100,EewTarget(group,devices[i]),true))}
            val first=QuakeStore(dbs[0]).states(group).single().formation.incidentId
            QuakeStore(dbs[0]).command(QuakeEvent(group,id(),admin,issued+50,QuakeAction.END,false,first))
            val end=QuakeStore(dbs[0]).events(group).single {it.action==QuakeAction.END};QuakeStore(dbs[1]).merge(end)
            assertTrue(QuakeStore(dbs[1]).states(group).single().closed)
            engines[0].connected("1");engines[1].connected("0");var delivered=0
            while(queue.isNotEmpty()){check(++delivered<1000);val(from,to,data)=queue.removeFirst();engines[to].receive(from.toString(),data)}
            assertEquals(emptyList<String>(),errors)
            assertEquals(QuakeStore(dbs[0]).states(group),QuakeStore(dbs[1]).states(group));assertTrue(QuakeStore(dbs[0]).states(group).single().closed)
            assertEquals(2,QuakeStore(dbs[0]).events(group).count {it.action==QuakeAction.EEW})
        }finally {dbs.forEach {it.close()};names.forEach {context.deleteDatabase(it)}}
    }
}
