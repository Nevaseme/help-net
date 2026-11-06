package io.github.nevaseme.plandmk1

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Replays paginated initial synchronization without writing to the public group. */
class VenueLateJoinDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun waitsForCurrentVenueThenAnswersAndNavigates() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val app=inst.targetContext.applicationContext as PlanDApplication
        val previous=app.installation.group.value
        val prefs=app.getSharedPreferences("interface-language",0)
        val language=prefs.getString("language",null)
        val models=ViewModelStore();val codec=Json{ignoreUnknownKeys=true}
        val raw=inst.context.assets.open("venue-experience-fixture.json").bufferedReader().use{it.readText()}
        val original=codec.parseToJsonElement(raw).jsonObject.getValue("envelope").jsonObject
        val device=original.getValue("roster").jsonObject.getValue("members").jsonArray.first().jsonObject.getValue("deviceIds").jsonArray.first().jsonPrimitive.content
        val group=UUID.randomUUID().toString()
        val payload=codec.parseToJsonElement(raw.replace(device,app.installation.localId).replace(original.getValue("groupId").jsonPrimitive.content,group)).jsonObject
        val source=codec.decodeFromJsonElement<CloudRosterEnvelope>(payload.getValue("envelope"))
        val envelope=source.copy(venue=source.venue!!.copy(presentationMode="EARTHQUAKE"))
        val venue=envelope.venue!!
        val old=QuakeEvent(group,UUID.randomUUID().toString(),app.installation.localId,venue.startedAt-100000,QuakeAction.ADMIN_START,false,title="Earlier earthquake",area="Kyoto")
        lateinit var model:PlanViewModel
        try {
            prefs.edit().putString("language","en").commit()
            runBlocking {
                RoomCloudJournal(app.database,group,app.installation.localId).updateRoster(envelope,app.installation.localId)
                app.quakes.merge(old)
            }
            CloudSettings(app).saveVenue(envelope)
            inst.runOnMainSync {
                org.maplibre.android.MapLibre.getInstance(app)
                app.installation.setGroup(group);app.installation.selectIncident(group,old.eventId)
                app.cloud.venueChanges.value++;model=PlanViewModel(app);models.put("late-join",model)
            }
            compose.waitUntil(15000){model.incidentUi.value.loadingVenue}
            shellComposeContent{PlanDMk1Theme{PlanApp(model)}}
            compose.onNodeWithText("Loading safety check…").assertIsDisplayed()
            compose.onNodeWithText("Save status and open map").assertDoesNotExist()
            assertFalse(model.incidentUi.value.canEdit)
            runBlocking {RoomCloudJournal(app.database,group,app.installation.localId).mergePage(codec.decodeFromJsonElement<List<CloudRecord>>(payload.getValue("records")))}
            compose.waitUntil(15000){model.incidentUi.value.selection==venue.incidentId&&model.activeVenue.value!=null}
            compose.onNodeWithText("I need help",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Save status and open map").performScrollTo().performClick()
            compose.waitUntil(15000){compose.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(Safety.SOS,model.incidentUi.value.current!!.answers[model.localId]?.safety)
            assertNotEquals(model.safetySaveKey(group,old.eventId),model.safetySaveKey(group,venue.incidentId))
            assertNull(runBlocking {app.store.incidents(group)}.find {it.kind==IncidentKind.ANSWER&&it.incidentId==old.eventId&&it.authorId==model.localId})
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Map").assertIsSelected()
            for(tab in listOf("People","Tasks","Safety")){compose.onNode(hasText(tab) and hasClickAction()).performClick();compose.onNode(hasText(tab) and hasClickAction()).assertIsSelected()}
            compose.onNodeWithText("I’m safe",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Update my status").performScrollTo().performClick()
            compose.waitUntil(15000){model.incidentUi.value.current?.answers?.get(model.localId)?.safety==Safety.SAFE}
            compose.onNode(hasText("Safety") and hasClickAction()).assertIsSelected();compose.onNodeWithText("Continue").assertDoesNotExist()
        } finally {
            inst.runOnMainSync{models.clear();app.installation.setGroup(previous);ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach{it.finish()}}
            prefs.edit().apply{if(language==null)remove("language")else putString("language",language)}.commit()
        }
    }
}
