package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.demo.*
import io.github.nevaseme.plandmk1.ui.*
import org.junit.Assert.*
import org.junit.Test

class VenueLocationTimeTest {
    private val device="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val session="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private val placement=DemoPlacement(session,device,device,"Participant",0,35.0145,135.7625)
    private val venue=VenueSession(groupId="group",sessionId=session,incidentId=session,areaId="kyoto-gosho-south",startedAt=1000,placements=listOf(placement))
    private val fix=PhonePosition("group",device,1,2345,35.0145,135.7625,0.0)

    @Test fun kyotoDisplaysOnlyMatchingReceivedAcquisitionTimes() {
        assertNull(venue.assignedPositionTime(placement,null))
        assertEquals(2345L,venue.assignedPositionTime(placement,fix))
        for(invalid in listOf(fix.copy(acquiredAt=999),fix.copy(groupId="other"),fix.copy(originId=session),fix.copy(latitude=35.6665,longitude=139.7495),fix.copy(accuracyMeters=10.0)))assertNull(venue.assignedPositionTime(placement,invalid))
        assertNull(venue.copy(positionMode="ACTUAL").assignedPositionTime(placement,fix))
        assertEquals(1000L,venue.assignedPositionTime(placement.copy(source="SCENARIO"),null))
    }

    @Test fun assignedPointRemainsVisibleWithoutPretendingItsTimeIsKnown() {
        val assigned=MapPerson(device,"Participant",MapSafety.SOS,point=MapPoint(35.0145,135.7625),assignedPosition=true,demo=true)
        assertTrue(assigned.hasPosition(5000));assertNull(assigned.acquiredAt)
        assertEquals("日時不明",positionDescription(assigned,5000))
        assertFalse(assigned.copy(assignedPosition=false).hasPosition(5000))
        assertFalse(assigned.copy(point=null,assignedPosition=false,fictional=true,lastKnownAt=100).hasPosition(5000))
        assertTrue(assigned.copy(assignedPosition=false,acquiredAt=2345).hasPosition(5000))
    }

    @Test fun lastKnownPositionsValidateCoordinatesAndWireSafeTimestamps() {
        val valid=LastKnownPosition(35.0145,135.7625,1000)
        valid.validate()
        for(invalid in listOf(valid.copy(latitude=Double.NaN),valid.copy(latitude=90.0),valid.copy(longitude=181.0),valid.copy(acquiredAt=-1),valid.copy(acquiredAt=9_007_199_254_740_992L))){
            try {invalid.validate();fail("Invalid last-known position accepted: $invalid")}catch(expected:IllegalArgumentException){}
        }
        val sampleId="cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        val home=DemoHome(session,"Home",35.0145,135.7625,null)
        val sample=ScenarioPerson(sampleId,"Resident","UNREPORTED","UNKNOWN","","west",home,lastKnownPosition=valid)
        val scenario=VenueScenario(sessionId=session,communities=listOf(DemoCommunity("west","西","West")),people=listOf(sample))
        val roster=MemberRoster(groupId="group",members=listOf(GroupMember(device,"Participant",MemberRole.ADMIN,listOf(device))))
        venue.copy(scenario=scenario).validate(roster)
        try {venue.copy(scenario=scenario.copy(people=listOf(sample.copy(lastKnownPosition=valid.copy(acquiredAt=-1))))).validate(roster);fail("Venue accepted invalid history")}catch(expected:IllegalArgumentException){}
    }
}
