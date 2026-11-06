package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.demo.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class VenuePopulationTest {
    @Test fun acceptsExpandedResidentPoolAndPreservesExplicitSensorPatterns() {
        val session=UUID.randomUUID().toString()
        val device=UUID.randomUUID().toString()
        val home=DemoHome(UUID.randomUUID().toString(),"Home",35.0145,135.764,null)
        val patterns=listOf(false to false,true to true,true to false,false to false,true to true,false to true)
        val people=(0 until 128).map { i->
            val flags=patterns.getOrNull(i)?: (false to false)
            ScenarioPerson(UUID.randomUUID().toString(),"Resident $i",if(i<3)"SOS" else if(i<6)"UNREPORTED" else "SAFE","UNKNOWN","","demo-central",home,DemoObservations(flags.first,flags.second))
        }
        val venue=VenueSession(groupId="group",sessionId=session,incidentId=session,areaId="kyoto-gosho-south",startedAt=1000,placements=emptyList(),scenario=VenueScenario(sessionId=session,communities=listOf(DemoCommunity("demo-central","中央","Central")),people=people))
        val roster=MemberRoster(groupId="group",members=listOf(GroupMember(device,"Organizer",MemberRole.ADMIN,listOf(device))))
        venue.validate(roster)
        patterns.forEachIndexed { i,flags->assertEquals(1000L.takeIf{flags.first} to 1000L.takeIf{flags.second},venue.observationTimes(people[i].deviceId)) }
        try {venue.copy(scenario=venue.scenario!!.copy(people=people+people.first().copy(deviceId=UUID.randomUUID().toString()))).validate(roster);fail("Accepted an oversized resident pool")}catch(expected:IllegalArgumentException){}
    }
}
