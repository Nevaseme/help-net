package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.demo.*
import io.github.nevaseme.plandmk1.ui.*
import org.junit.Assert.*
import org.junit.Test

class DemoClockLocationTest {
    private val home=DemoHome("building","Reviewed home",35.014,135.759,null)
    private val person=ScenarioPerson("person","Casey","UNREPORTED","UNKNOWN","","demo-west",home,position=DemoPhonePosition(35.016,135.7585))
    private fun venue()=VenueSession(groupId="g",sessionId="s",incidentId="s",areaId="kyoto-gosho-south",placements=emptyList(),startedAt=1000000,simulationStartedAt=820000,scenario=VenueScenario(sessionId="s",communities=emptyList(),people=listOf(person)))
    @Test fun simulatedPhoneNeverReplacesReviewedHomeOrLegacyFallback(){
        val v=venue();val p=v.allPlacements().single();assertEquals(35.016,p.latitude!!,0.0);assertEquals(home,p.home)
        assertEquals(home.latitude,v.placeRecords().first {it.kind==PlaceKind.BUILDING}.latitude!!,0.0)
        assertEquals(home.latitude,v.copy(scenario=v.scenario!!.copy(people=listOf(person.copy(position=null)))).allPlacements().single().latitude!!,0.0)
    }
    @Test fun clockChangesOnlyDemoStartProjectionAndDisclosure(){
        val start=IncidentEvent("g","s","s","admin","Admin",1,1000000,IncidentKind.START)
        val original=IncidentView(start,false,emptyMap(),emptyList());val v=venue()
        val shown=v.projectClock(original);assertEquals(820000L,shown.start.createdAt);assertEquals(1000000L,original.start.createdAt)
        val map=MapPerson("person","Casey",MapSafety.UNANSWERED,MapPoint(35.016,135.7585),1000000,home=MapHome("building","Home",MapPoint(home.latitude,home.longitude)),demo=true,fictional=true)
        assertTrue(projectPersonPrivacy(map,shown.start.createdAt,1000000).identityVisible)
        assertFalse(projectPersonPrivacy(map,v.copy(simulationStartedAt=1000000).projectClock(original).start.createdAt,1000000).identityVisible)
        assertEquals(original,v.copy(closed=true).projectClock(original))
        assertEquals(original,v.copy(sessionId="other").projectClock(original))
        assertEquals(1000000L,v.scenarioJournal().firstOrNull()?.createdAt?:1000000L)
    }
    @Test fun tsunamiEnglishAndLegacyApproximateAddressRemainDisplayOnly(){
        listOf("title","height","legend","depth","deep","preparing","enter","range","loading","result","cancelled","off","error").forEach {assertFalse(tsunamiText(it,true,"20").contains(Regex("[\\u3040-\\u9fff]")))}
        assertEquals("Near Sanbongi Gochome, Higashinotoin-dori Takeyamachi, Nakagyo-ku, Kyoto",demoAddressLabel("京都府京都市中京区三本木五丁目東洞院通竹屋町付近","en"))
    }
}
