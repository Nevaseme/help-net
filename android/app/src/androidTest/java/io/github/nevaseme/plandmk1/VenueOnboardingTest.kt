package io.github.nevaseme.plandmk1

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Fictional public venue, normal controls. Never run on a physical device. */
class VenueOnboardingTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun id()=UUID.randomUUID().toString()
    private fun shot(app:PlanDApplication,name:String) {
        val dir=File(app.filesDir,"venue-proof").apply {mkdirs()}
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap->
            File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    private fun until(check:suspend()->Boolean)=runBlocking {withTimeout(60_000){while(!check())delay(100)}}
    @Test fun creationFromUnansweredDemoStaysOnGroupPreparation() {
        assumeTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val endpoint=InstrumentationRegistry.getArguments().getString("cloudEndpoint").orEmpty();assumeTrue(endpoint.isNotBlank())
        val app=compose.activity.application as PlanDApplication;val previous=app.installation.group.value
        val api=CloudHttpApi();val settings=CloudSettings(app,"create-flow-${id()}")
        val admin=runBlocking {val pending=settings.begin(endpoint,app.installation.localId,"作成確認","旧デモ-${id().take(8)}");api.register(endpoint,pending.path,pending.body)}
        runBlocking {api.startVenue(admin.session(endpoint),id(),"Creation-Test-9726")};CloudSettings(app).complete(admin.session(endpoint))
        try {
            compose.runOnIdle {app.installation.setGroup(admin.groupId)}
            until {app.cloud.state.value.groupId==admin.groupId&&app.cloud.state.value.admin}
            compose.runOnIdle {app.cloud.sync()}
            compose.waitUntil(30_000){compose.onAllNodesWithText("安否を登録して地図へ").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("メニューを開く").performClick()
            compose.onNodeWithText("グループ",useUnmergedTree=true).performScrollTo().performClick()
            compose.onNodeWithText("グループの設定").performScrollTo().performClick()
            compose.onNodeWithText("別のグループに参加").performScrollTo().performClick()
            compose.onNodeWithText("管理者としてグループを作成").performScrollTo().performClick()
            compose.onNode(hasText("グループ名") and hasSetTextAction()).performScrollTo().performTextInput("新しい準備-${id().take(8)}")
            compose.onNode(hasText("あなたの名前") and hasSetTextAction()).performScrollTo().performTextReplacement("作成確認")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("作成してデモを準備").performScrollTo().performClick()
            until {app.cloud.state.value.groupId!=admin.groupId&&app.cloud.state.value.connected&&app.cloud.state.value.admin}
            compose.waitUntil(5_000){compose.onAllNodesWithText("デモを準備しましょう").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("参加パスワード").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("デモを開始する").performScrollTo().assertIsDisplayed()
            shot(app,"created-group-preparation")
        }finally{compose.runOnIdle {app.sharing.stop();app.installation.setGroup(previous)}}
    }
    @Test fun administratorStartsEndsAndRehearsesFromGroupScreen() {
        assumeTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val endpoint=InstrumentationRegistry.getArguments().getString("cloudEndpoint").orEmpty();assumeTrue(endpoint.isNotBlank())
        val app=compose.activity.application as PlanDApplication;val previous=app.installation.group.value
        val api=CloudHttpApi();val settings=CloudSettings(app,"admin-flow-${id()}")
        val admin=runBlocking {val pending=settings.begin(endpoint,app.installation.localId,"架空管理者","リハーサル-${id().take(8)}");api.register(endpoint,pending.path,pending.body)}
        CloudSettings(app).complete(admin.session(endpoint))
        try {
            compose.runOnIdle {app.installation.setGroup(admin.groupId)}
            until {app.cloud.state.value.groupId==admin.groupId&&app.cloud.state.value.admin}
            compose.onNodeWithContentDescription("メニューを開く").performClick()
            compose.onNodeWithText("グループ",useUnmergedTree=true).performScrollTo().performClick()
            compose.onNodeWithText("参加パスワード").performScrollTo().performTextInput("Rehearsal-Test-9726")
            androidx.test.espresso.Espresso.closeSoftKeyboard();shot(app,"admin-prepare")
            compose.onNodeWithText("デモを開始する").performScrollTo().performClick()
            until {app.cloud.state.value.venue?.closed==false}
            val first=checkNotNull(app.cloud.state.value.venue);assertEquals("KYOTO",first.positionMode)
            // Sync selects the new incident and takes the administrator to safety too.
            compose.waitUntil(30_000){compose.onAllNodesWithText("安否を登録して地図へ").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("メニューを開く").performClick()
            compose.onNodeWithText("グループ",useUnmergedTree=true).performScrollTo().performClick()
            shot(app,"admin-running")
            compose.onNodeWithText("デモを終了する").performScrollTo().performClick();compose.onNodeWithText("終了する",useUnmergedTree=true).performClick()
            until {app.cloud.state.value.venue?.closed==true}
            compose.onNodeWithContentDescription("メニューを開く").performClick()
            compose.onNodeWithText("グループ",useUnmergedTree=true).performScrollTo().performClick()
            compose.onNodeWithText("参加者の現在地").performScrollTo().performClick()
            compose.onNodeWithText("参加パスワード").performScrollTo().performTextInput("Rehearsal-Test-9726")
            androidx.test.espresso.Espresso.closeSoftKeyboard();shot(app,"admin-actual-mode")
            compose.onNodeWithText("デモを開始する").performScrollTo().performClick()
            until {app.cloud.state.value.venue?.let {!it.closed&&it.sessionId!=first.sessionId}==true}
            assertEquals("ACTUAL",app.cloud.state.value.venue!!.positionMode)
        }finally{compose.runOnIdle {app.sharing.stop();app.installation.setGroup(previous)}}
    }
    private fun currentMap():org.maplibre.android.maps.MapLibreMap {
        val result=java.util.concurrent.atomic.AtomicReference<org.maplibre.android.maps.MapLibreMap>()
        fun maps(view:android.view.View):List<org.maplibre.android.maps.MapView> = when(view){is org.maplibre.android.maps.MapView->listOf(view);is android.view.ViewGroup->(0 until view.childCount).flatMap {maps(view.getChildAt(it))};else->emptyList()}
        compose.waitUntil(30000) {compose.runOnIdle {maps(compose.activity.window.decorView).firstOrNull()?.getMapAsync {result.set(it)}};result.get()!=null}
        return result.get()
    }
    @Test fun joinAfterAdministratorStartAnswerAndOpenMapWithoutHome() {
        assumeTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val endpoint=InstrumentationRegistry.getArguments().getString("cloudEndpoint").orEmpty()
        assumeTrue(endpoint.isNotBlank())
        val app=compose.activity.application as PlanDApplication;val previous=app.installation.group.value
        val api=CloudHttpApi();val groupName="架空Android会場-${id().take(8)}";val password="fictional-venue-pass"
        val adminSettings=CloudSettings(app,"venue-test-${id()}")
        val admin=runBlocking {val pending=adminSettings.begin(endpoint,id(),"先に参加した主催者",groupName);api.register(endpoint,pending.path,pending.body)}
        val incident=id();runBlocking {api.startVenue(admin.session(endpoint),incident,password)
            api.push(admin.session(endpoint),listOf(CloudRecord("incidents",Json.encodeToJsonElement(IncidentEvent(admin.groupId,id(),incident,admin.deviceId,"先に参加した主催者",2,System.currentTimeMillis(),IncidentKind.ANSWER,safety=Safety.SOS)).jsonObject)))
        }
        var stage="entry"
        try {
            compose.runOnIdle {app.installation.setGroup("venue-ui-${id().take(8)}")}
            if(compose.onAllNodesWithText("インターネットで共有").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("戻る").performClick()
            compose.onNodeWithText("地図",useUnmergedTree=true).performClick()
            val oldMap=currentMap()
            compose.runOnIdle {oldMap.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(35.68,139.76),15.0))}
            compose.onAllNodes(isToggleable()).onFirst().performClick()
            if(compose.onAllNodesWithText("インターネットで共有").fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithContentDescription("メニューを開く").performClick()
                compose.onNodeWithText("グループ",useUnmergedTree=true).performScrollTo().performClick()
            }
            compose.onNodeWithText("グループ名").performScrollTo().performTextInput(groupName)
            compose.onNodeWithText("参加パスワード").performScrollTo().performTextInput(password)
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            assertEquals(password,app.cloud.venuePasswordDraft.value)
            shot(app,"01-credentials")
            compose.onNodeWithText("次へ").performScrollTo().performClick()
            compose.waitUntil(30_000){compose.onAllNodesWithText("あなたの名前").fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasText("あなたの名前") and hasSetTextAction()).performTextInput("Android会場参加者")
            shot(app,"02-name")
            compose.onNodeWithText("参加して安否を回答").performScrollTo().performClick()
            stage="join-and-start"
            until {app.installation.group.value==admin.groupId&&app.installation.incidentSelection(admin.groupId)==incident}
            compose.waitUntil(30_000){compose.onAllNodesWithText("安否を登録して地図へ").fetchSemanticsNodes().isNotEmpty()}
            shot(app,"03-safety")
            compose.onNodeWithText("無事").performClick()
            compose.onNodeWithText("安否を登録して地図へ").performScrollTo().performClick()
            stage="answer-local"
            until {app.store.incidents(admin.groupId).any {it.authorId==app.installation.localId&&it.kind==IncidentKind.ANSWER&&it.safety==Safety.SAFE}}
            compose.waitUntil(30_000){compose.onAllNodesWithText("安否を登録して地図へ").fetchSemanticsNodes().isEmpty()}
            val map=currentMap();var centered=false;var visibleMarkers=false
            until {var ready=false;compose.runOnIdle {ready=map.style!=null};ready}
            runBlocking {withTimeoutOrNull(10000){while(!visibleMarkers){compose.runOnIdle {visibleMarkers=map.markers.filter {map.projection.visibleRegion.latLngBounds.contains(it.position)}.sumOf {marker->Regex("([0-9]+)人").findAll(marker.title.orEmpty()).sumOf {it.groupValues[1].toInt()}}>=2};delay(100)}}}
            compose.runOnIdle {centered=map.cameraPosition.target!!.latitude in 35.011..35.018&&map.cameraPosition.target!!.longitude in 135.758..135.767;visibleMarkers=map.markers.filter {map.projection.visibleRegion.latLngBounds.contains(it.position)}.sumOf {marker->Regex("([0-9]+)人").findAll(marker.title.orEmpty()).sumOf {it.groupValues[1].toInt()}}>=2}
            val model=androidx.lifecycle.ViewModelProvider(compose.activity)[PlanViewModel::class.java]
            compose.runOnIdle {model.saveCare(admin.deviceId,CareAction.RESCUED,"会場試験で救助を確認")}
            var careSaved=false
            runBlocking {withTimeoutOrNull(12000){while(!careSaved){careSaved=app.store.incidents(admin.groupId).any {it.kind==IncidentKind.CARE};delay(100)}}}
            assertTrue("Venue must recenter old Tokyo viewport, restore SAFE markers, and save no-home care: centered=$centered markers=$visibleMarkers care=$careSaved",centered&&visibleMarkers&&careSaved)
            // Native renderer completion is separate from Compose idle and marker registration.
            stage="map-pixels"
            compose.runOnIdle {
                val dir=File(app.filesDir,"venue-proof")
                File(dir,"map-state.txt").writeText(map.markers.joinToString("\n") {"${it.id} ${it.title} ${it.position} ${map.projection.toScreenLocation(it.position)} ${it.icon?.bitmap?.config}"})
                File(dir,"map-layers.txt").writeText(map.style!!.layers.joinToString("\n") {"${it.id} ${it.javaClass.simpleName} ${it.visibility}"})
                map.markers.forEachIndexed {i,m->m.icon?.bitmap?.let {bmp->File(dir,"icon-$i.png").outputStream().use {bmp.compress(Bitmap.CompressFormat.PNG,100,it)}}}
                map.snapshot {bmp->File(dir,"native-map.png").outputStream().use {bmp.compress(Bitmap.CompressFormat.PNG,100,it)}}
            }
            until {
                val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                var green=0
                for(y in bitmap.height/3 until bitmap.height*4/5 step 3) for(x in 0 until bitmap.width step 3) {
                    val color=bitmap.getPixel(x,y)
                    if(android.graphics.Color.red(color)<30&&android.graphics.Color.green(color) in 110..160&&android.graphics.Color.blue(color) in 80..130)green++
                }
                bitmap.recycle();green>50
            }
            compose.waitForIdle();assertNull(model.message.value);shot(app,"04-map")
            val venue=CloudSettings(app).venue(admin.groupId)!!
            assertEquals(2,venue.placements.size)
            assertTrue(venue.active(admin.groupId,incident))
            runBlocking {assertNull(app.store.get(admin.groupId,app.installation.localId));assertTrue(app.store.positions(admin.groupId).isEmpty())}
            stage="answer-cloud"
            until {val shared=api.pull(admin.session(endpoint),0).records;listOf("ANSWER","CARE").all {kind->shared.any {it.collection=="incidents"&&it.record["kind"]?.jsonPrimitive?.content==kind&&it.record["authorId"]?.jsonPrimitive?.content==app.installation.localId}}}
            compose.activityRule.scenario.recreate();compose.waitForIdle()
            assertEquals(venue,CloudSettings(app).venue(admin.groupId))
            assertEquals(incident,app.installation.incidentSelection(admin.groupId))
        }catch(e:Throwable){shot(app,"failure");throw AssertionError("stage=$stage groupJoined=${app.installation.group.value==admin.groupId} selected=${app.installation.incidentSelection(admin.groupId)} status=${app.cloud.state.value.status} error=${app.cloud.state.value.error} pending=${CloudSettings(app).pending()!=null}",e)}finally {compose.runOnIdle {app.sharing.stop();app.installation.setGroup(previous)}}
    }
}
