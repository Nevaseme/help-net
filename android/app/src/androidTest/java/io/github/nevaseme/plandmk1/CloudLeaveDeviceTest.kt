package io.github.nevaseme.plandmk1

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Uses only an explicitly supplied disposable HTTPS server; restores the user's group. */
class CloudLeaveDeviceTest {
 @get:Rule val compose=createEmptyComposeRule()
 @Test fun memberCanCancelLeaveAndRejoinWhileAdminCannotLeave()=runBlocking<Unit> {
  val inst=InstrumentationRegistry.getInstrumentation()
  val endpoint=checkNotNull(InstrumentationRegistry.getArguments().getString("cloudEndpoint"))
  require(endpoint=="https://localhost:9443")
  val app=inst.targetContext.applicationContext as PlanDApplication
  val previous=app.installation.group.value;val settings=CloudSettings(app);val api=CloudHttpApi()
  fun id()=UUID.randomUUID().toString()
  fun token()=id().replace("-","")+id().replace("-","")
  val groupName="Leave test "+id().take(8);val password="LeaveTest123"
  val admin=api.register(endpoint,"groups",buildJsonObject{put("requestId",id());put("deviceId",id());put("activityName","Organizer");put("groupName",groupName);put("password",password);put("registrationToken",token())})
  val adminSession=admin.session(endpoint)
  api.startVenue(adminSession,id(),"",presentationMode="EARTHQUAKE")
  val member=api.register(endpoint,"venue/join",buildJsonObject{put("deviceId",app.installation.localId);put("activityName","Participant");put("groupName",groupName);put("password",password);put("registrationToken",token())})
  val session=member.session(endpoint)
  try {
   settings.complete(session);settings.saveVenue(member.envelope())
   RoomCloudJournal(app.database,session.groupId,session.deviceId).updateRoster(member.envelope(),session.deviceId)
   lateinit var model:PlanViewModel
   inst.runOnMainSync{org.maplibre.android.MapLibre.getInstance(app);app.installation.setGroup(session.groupId);model=PlanViewModel(app)}
   compose.waitUntil(15000){app.cloud.state.value.groupId==session.groupId&&app.cloud.state.value.connected}
   shellComposeContent {CompositionLocalProvider(LocalUiLanguage provides "en"){PlanDMk1Theme{Surface{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){CloudSharingPanel(model,"Participant")}}}}}
   compose.onNodeWithText("Group settings").performScrollTo().performClick()
   compose.onNodeWithText("Leave group").performScrollTo().performClick()
   compose.onNodeWithText("Cancel").performClick()
   assertNotNull(settings.session(session.groupId))
   compose.onNodeWithText("Leave group").performScrollTo().performClick()
   compose.onAllNodesWithText("Leave group").filterToOne(hasClickAction() and hasAnyAncestor(isDialog())).performClick()
   compose.waitUntil(30000){!app.cloud.state.value.connected&&!app.cloud.state.value.busy}
   assertNull(settings.session(session.groupId));assertNull(settings.nearbyKey(session.groupId))
   compose.onNodeWithText("Group name").assertExists()
   assertFalse(api.roster(adminSession).venue!!.placements.any{it.deviceId==session.deviceId})
   inst.runOnMainSync{app.cloud.joinVenue(endpoint,groupName,password,"Participant")}
   compose.waitUntil(30000){app.cloud.state.value.connected&&!app.cloud.state.value.busy}
   assertEquals(session.memberId,settings.session(session.groupId)!!.memberId)
   assertEquals(2,api.roster(adminSession).venue!!.placements.size)
   var rejected=false;try{api.leaveGroup(adminSession)}catch(e:CloudHttpException){rejected=e.status==403};assertTrue(rejected)
  }finally{inst.runOnMainSync{app.installation.setGroup(previous)};settings.leaveGroup(session.groupId)}
 }
}
