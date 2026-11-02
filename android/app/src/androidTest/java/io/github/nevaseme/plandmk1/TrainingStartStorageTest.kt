package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrainingStartStorageTest {
    @Test fun explicitTrainingAndMaterializedIncidentSurviveDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "training-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, PlanDatabase::class.java, name).build()
        var db = open()
        val admin = UUID.randomUUID().toString()
        val member = UUID.randomUUID().toString()
        val roster = MemberRoster(groupId="trial", members=listOf(
            GroupMember(admin, "管理者", MemberRole.ADMIN, listOf(admin)),
            GroupMember(member, "参加者", MemberRole.MEMBER, listOf(member)),
        ))
        val start = QuakeEvent("trial", UUID.randomUUID().toString(), admin, 1, QuakeAction.TRAINING_START, true, title="明示訓練", area="架空地域")
        try {
            var store = QuakeStore(db)
            store.importRoster(roster.encode(), admin)
            try { store.command(start.copy(actorDeviceId=member)); fail("Members cannot start training") }
            catch (_: IllegalArgumentException) { }
            assertTrue(store.events("trial").isEmpty())
            store.command(start)
            db.close(); db = open(); store = QuakeStore(db)
            assertEquals(listOf(start), store.events("trial"))
            val state = store.states("trial").single()
            assertEquals(start.eventId, state.formation.incidentId)
            assertTrue(state.formation.training)
            assertFalse(state.closed)
            val incident = RoomRecordStore(db).incidents("trial").single()
            assertEquals(start.eventId, incident.eventId)
            assertTrue(incident.training)
            assertEquals("明示訓練", incident.title)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
