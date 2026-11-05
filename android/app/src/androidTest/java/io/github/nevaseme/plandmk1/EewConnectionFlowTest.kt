package io.github.nevaseme.plandmk1

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Visible app flow, emulator only, using a new fictional group and restoring the previous group. */
class EewConnectionFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun backgroundWarningReplacesTrainingSelectionThenAnswerOpensMap()=runBlocking<Unit> {
        assertTrue("Emulator-only fictional UI fixture",Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val app=compose.activity.application as PlanDApplication;val previous=app.installation.group.value
        val group="eew-ui-${UUID.randomUUID().toString().take(8)}";val me=app.installation.localId;val admin=UUID.randomUUID().toString()
        val roster=MemberRoster(groupId=group,members=listOf(GroupMember(admin,"架空管理者",MemberRole.ADMIN,listOf(admin)),GroupMember(me,"架空受信者",MemberRole.MEMBER,listOf(me))))
        fun model()=ViewModelProvider(compose.activity)[PlanViewModel::class.java]
        try {
            app.quakes.importRoster(roster.encode(),me)
            app.store.merge(Resident(group,me,1,1,"架空受信者",35.0,135.75,"1階","試験室",Safety.UNREPORTED))
            for((index,actor) in listOf(admin,me).withIndex())app.quakes.command(QuakeEvent(group,UUID.randomUUID().toString(),actor,100+index.toLong(),QuakeAction.REPORT,true,title="架空訓練",area="試験地区"))
            val training=app.quakes.states(group).single().formation.incidentId
            compose.runOnIdle {app.installation.setGroup(group);app.installation.selectIncident(group,training)}
            compose.waitUntil(15000){model().incidentUi.value.selection==training}
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val now=System.currentTimeMillis();val format=SimpleDateFormat("yyyy/MM/dd HH:mm:ss",Locale.ROOT).apply {timeZone=TimeZone.getTimeZone("Asia/Tokyo")}
            val event=SimpleDateFormat("yyyyMMddHHmmss",Locale.ROOT).format(now+315360000000L)
            val data=mapOf("schema" to "pland-eew-1","source" to "wolfx","region" to "京都府南部",
                "raw" to """{"type":"jma_eew","EventID":"$event","Serial":1,"AnnouncedTime":"${format.format(now)}","Issue":{"Status":"通常"},"isTraining":false,"isWarn":true,"isCancel":false,"WarnArea":[{"Chiiki":"京都府南部","Type":"警報"}]}""")
            assertNotNull(app.eewReceiver.receive(data,now))
            assertEquals(Lifecycle.State.CREATED,compose.activityRule.scenario.state)
            val actual=app.quakes.states(group).single {!it.formation.training}.formation.incidentId
            assertEquals(actual,app.installation.incidentSelection(group))
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(15000){model().incidentUi.value.selection==actual&&model().incidentUi.value.canEdit}
            compose.onNodeWithText("地震発生").assertIsDisplayed()
            compose.onNodeWithText("いまの安否を教えてください").assertIsDisplayed()
            compose.onNodeWithText("無事").assertIsDisplayed().performClick()
            compose.onNodeWithText("安否を登録して地図へ").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(15000){model().incidentUi.value.current?.answers?.get(me)?.safety==Safety.SAFE}
            compose.onNodeWithText("地図").assertIsSelected()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000){model().incidentUi.value.selection==actual&&model().incidentUi.value.current?.answers?.get(me)?.safety==Safety.SAFE}
            compose.onNodeWithText("地図").assertIsSelected()
            assertEquals(2,app.quakes.states(group).size)
        }finally {
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnIdle {app.installation.setGroup(previous)}
        }
    }
}
