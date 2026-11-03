package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.demo.*
import io.github.nevaseme.plandmk1.ui.*
import org.junit.Assert.*
import org.junit.Test

class NeighborhoodPresentationTest {
    @Test fun mapShortfallsExcludeFinishedWorkWithoutSuppressingConcurrentActiveWork() {
        val done=ResponseTarget(TargetKind.REPORT,"done");val conflict=ResponseTarget(TargetKind.PERSON,"conflict")
        val changes=listOf(ResponseChange.Material(done,"m1","水","本",4.0,1.0),ResponseChange.Material(conflict,"m2","水","本",5.0,1.0),ResponseChange.Progress(done,WorkStatus.DONE,"完了"),ResponseChange.Progress(conflict,WorkStatus.DONE,"完了"),ResponseChange.Progress(conflict,WorkStatus.IN_PROGRESS,""))
        assertEquals(listOf("m2"),activeMaterialChanges(changes).filterIsInstance<ResponseChange.Material>().map {it.requestId})
    }
    @Test fun buildingDamageDoesNotCountAsCollapse() {
        assertEquals("OTHER",reportCategoryGroup("BUILDING_DAMAGE"))
        assertEquals("BUILDING",reportCategoryGroup("BUILDING_COLLAPSE"))
        assertEquals("BUILDING",reportCategoryGroup("建物倒壊"))
    }
    @Test fun aggregateUrgencyExcludesCompletedAndCancelledButRetainsConcurrentActiveProgress() {
        fun report(id:String,cancelled:Boolean=false)=ResponseChange.Report(id,"道路閉塞",35.0,135.0,"内容",listOf("west"),cancelled)
        fun target(id:String)=ResponseTarget(TargetKind.REPORT,id)
        val changes=listOf(report("open"),report("done"),report("cancelled",true),report("conflict"))+
            listOf("open","done","cancelled","conflict").map {ResponseChange.Priority(target(it),Urgency.HIGH,"確認")}+
            listOf(ResponseChange.Progress(target("done"),WorkStatus.DONE,"完了"),ResponseChange.Progress(target("conflict"),WorkStatus.DONE,"完了"),ResponseChange.Progress(target("conflict"),WorkStatus.IN_PROGRESS,""))
        assertEquals(2,activeHighReports(changes,"west"))
        assertEquals(0,activeHighReports(changes,"east"))
    }
    @Test fun neighborhoodUsesRegisteredHomeAndScenarioRegionNeverCurrentPhone() {
        val home=DemoHome("building","住所",35.014,135.759,null)
        val placement=DemoPlacement("s","participant","member","参加者",0,35.014,135.77,home=home)
        val scenario=VenueScenario(sessionId="s",communities=listOf(DemoCommunity("demo-west","西町内会","West")),people=listOf(ScenarioPerson("scenario","住民","SOS","UNKNOWN","","demo-west",home.copy(longitude=135.77))))
        val venue=VenueSession(groupId="g",sessionId="s",incidentId="s",areaId="kyoto",placements=listOf(placement),scenario=scenario)
        val rows=communityPeopleFor(venue.displayResidents(),emptyList(),venue)
        assertEquals("demo-west",rows.first {it.id=="participant"}.communityId)
        assertEquals("demo-west",rows.first {it.id=="scenario"}.communityId)
        val withoutHome=venue.copy(placements=listOf(placement.copy(home=null)))
        assertNull(communityPeopleFor(withoutHome.displayResidents(),emptyList(),withoutHome).first {it.id=="participant"}.communityId)
    }
    @Test fun venueNameExceptionDoesNotRevealKnownSosHome() {
        val person=MapPerson("p","参加者",MapSafety.SOS,MapPoint(35.0,135.0),1000,home=MapHome("h","住所",MapPoint(35.1,135.1)),bedroom="寝室",demo=true)
        val projected=projectPersonPrivacy(person,1000,1001)
        assertEquals("参加者",projected.publicName)
        assertTrue(projected.identityVisible)
        assertNull(projected.home)
        assertFalse(projected.registeredPlaceVisible)
        assertEquals("",projected.bedroom)
        assertNotNull(projectPersonPrivacy(person.copy(point=null,acquiredAt=null),1000,1001).home)
    }
}
