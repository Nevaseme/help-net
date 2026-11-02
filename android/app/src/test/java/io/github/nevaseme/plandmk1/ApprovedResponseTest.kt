package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import org.junit.Assert.*
import org.junit.Test

class ApprovedResponseTest {
    private val target=ResponseTarget(TargetKind.PERSON,"person")
    private fun event(id:String,change:ResponseChange)=ResponseEvent("group",id,"actor",1,1,"incident",change)
    @Test fun completedThenSafePersonRetainsCaseAndMaterialsWithoutIdentity(){
        val person=Resident("group","person",1,0,"公開しない氏名",35.0,135.0,"2階","寝室",safety=Safety.SAFE)
        val heads=listOf(event("material",ResponseChange.Material(target,"material","飲料水","本",4.0,1.0)),event("done",ResponseChange.Progress(target,WorkStatus.DONE,"完了")))
        val case=responseCases(listOf(person),emptyList(),heads).single()
        assertEquals(target,case.target);assertEquals(MapSafety.SAFE,case.person!!.state)
        assertNotEquals(person.name,case.title);assertEquals("",case.description)
        assertTrue(responseResolved(heads,target))
    }
    @Test fun mixedProgressNeverCountsAsResolved(){
        val done=event("done",ResponseChange.Progress(target,WorkStatus.DONE,"完了"))
        val active=event("active",ResponseChange.Progress(target,WorkStatus.IN_PROGRESS,"作業中"))
        assertFalse(responseResolved(emptyList(),target));assertFalse(responseResolved(listOf(done,active),target));assertTrue(responseResolved(listOf(done),target))
    }
    @Test fun recordedUnansweredIdentityStillWaitsForDisclosure(){
        val person=Resident("group","person",1,0,"公開しない氏名",35.0,135.0,"2階","寝室")
        val heads=listOf(event("assigned",ResponseChange.Assignment(target,"member",true)))
        assertEquals("応答なし",responseCases(listOf(person),emptyList(),heads,incidentStartedAt=1000,now=180999).single().title)
        assertEquals(person.name,responseCases(listOf(person),emptyList(),heads,incidentStartedAt=1000,now=181000).single().title)
    }
    @Test fun cancelledConflictStaysVisibleAndUrgencyUsesConservativeHead(){
        val report=ResponseChange.Report("report","道路閉塞",35.0,135.0,"内容")
        val revisions=listOf(event("a",report),event("b",report.copy(cancelled=true,cancelReason="訂正")))
        assertFalse(responseCases(emptyList(),emptyList(),revisions).single().cancelled)
        assertTrue(responseCases(emptyList(),emptyList(),listOf(revisions.last())).single().cancelled)
        assertEquals(Urgency.UNSET,responseUrgency(emptyList(),target))
        assertEquals(Urgency.HIGH,responseUrgency(listOf(event("high",ResponseChange.Priority(target,Urgency.HIGH,"危険")),event("low",ResponseChange.Priority(target,Urgency.LOW,"低い"))),target))
        assertNotEquals(reportIcon(Urgency.UNSET),reportIcon(Urgency.LOW))
    }
}
