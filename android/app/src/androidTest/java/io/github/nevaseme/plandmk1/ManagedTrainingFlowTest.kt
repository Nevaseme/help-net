package io.github.nevaseme.plandmk1

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Normal ViewModel entry point; isolated fixture groups, no physical-device test. */
class ManagedTrainingFlowTest {
    private fun id() = UUID.randomUUID().toString()
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private suspend fun until(check: suspend () -> Boolean) {
        withTimeout(10_000) { while (!check()) delay(20) }
    }

    private suspend fun withModel(role: MemberRole?, verify: suspend (PlanDApplication, String, PlanViewModel) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<PlanDApplication>()
        val previous = app.installation.group.value
        val group = "training-${id().take(8)}"
        val me = app.installation.localId
        val peer = id()
        if (role != null) {
            val roster = MemberRoster(groupId=group, members=listOf(
                GroupMember(me, "自分", role, listOf(me)),
                GroupMember(peer, "別の参加者", if (role == MemberRole.ADMIN) MemberRole.MEMBER else MemberRole.ADMIN, listOf(peer)),
            ))
            app.quakes.importRoster(roster.encode(), me)
        }
        app.store.updateOwn(group, me) { Resident(group, me, 1, 0, "架空試験者", 35.0, 139.0, "2階", "試験室") }
        val models = ViewModelStore()
        lateinit var model: PlanViewModel
        try {
            main { org.maplibre.android.MapLibre.getInstance(app); app.installation.setGroup(group); model = PlanViewModel(app); models.put("training", model) }
            until { model.incidentUi.value.groupId == group && (role == null || model.roster.value?.groupId == group) }
            verify(app, group, model)
        } finally { main { models.clear(); app.installation.setGroup(previous) } }
    }

    @Test fun administratorStartsTrainingAloneWithoutCreatingAHumanReport() = runBlocking {
        withModel(MemberRole.ADMIN) { app, group, model ->
            assertTrue(model.canStartTraining())
            main { model.startIncident("明示訓練", "架空地区", true) }
            until { !model.saving.value && model.saveStates.value["incident"]?.phase == SavePhase.SAVED }
            val command = app.quakes.events(group).single()
            assertEquals(QuakeAction.TRAINING_START, command.action)
            assertTrue(command.training)
            assertEquals(command.eventId, app.installation.incidentSelection(group))
            until { model.incidentUi.value.current?.start?.eventId == command.eventId }
            assertTrue(model.incidentUi.value.current!!.start.training)
            assertTrue(model.incidentUi.value.canEdit)
            main { model.startIncident("重複訓練", "架空地区", true) }
            until { !model.saving.value && model.saveStates.value["incident"]?.phase == SavePhase.ERROR }
            assertEquals(listOf(command), app.quakes.events(group))
        }
    }

    @Test fun memberCannotStartTrainingThroughViewModelAndActualReportStillNeedsQuorum() = runBlocking {
        withModel(MemberRole.MEMBER) { app, group, model ->
            assertFalse(model.canStartTraining())
            main { model.startIncident("不正な訓練開始", "架空地区", true) }
            until { !model.saving.value }
            assertEquals(SavePhase.ERROR, model.saveStates.value["incident"]?.phase)
            assertTrue(app.quakes.events(group).isEmpty())
            assertTrue(app.store.incidents(group).isEmpty())
            main { model.startIncident("実災害の報告", "架空地区", false) }
            until { !model.saving.value && model.saveStates.value["incident"]?.phase == SavePhase.SAVED }
            assertEquals(QuakeAction.REPORT, app.quakes.events(group).single().action)
            assertFalse(app.quakes.events(group).single().training)
            assertTrue(app.quakes.states(group).isEmpty())
        }
    }

    @Test fun missingParticipantAuthorityCannotFallBackToLegacyTrainingStart() = runBlocking {
        withModel(null) { app, group, model ->
            assertFalse(model.canStartTraining())
            main { model.startIncident("管理者未確認", "架空地区", true) }
            until { !model.saving.value }
            assertEquals(SavePhase.ERROR, model.saveStates.value["incident"]?.phase)
            assertTrue(app.store.incidents(group).isEmpty())
            assertTrue(app.quakes.events(group).isEmpty())
        }
    }
}
