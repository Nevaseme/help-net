package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class VenueStartupSelectionTest {
    private fun start(at: Long): IncidentEvent {
        val id=UUID.randomUUID().toString()
        return IncidentEvent("test",id,id,UUID.randomUUID().toString(),"Organizer",1,at,IncidentKind.START,title="Earthquake",area="Kyoto")
    }
    @Test fun lateJoinWaitsForCurrentVenueInsteadOfAnsweringEarlierOpenEarthquake() {
        val earlier=start(100);val current=start(200)
        val firstPage=incidentOptions(listOf(earlier))
        val pending=preferredLiveIncident(firstPage,earlier.eventId,current.eventId)
        assertEquals("",pending)
        assertFalse(projectIncidentUi("test",emptyList(),emptyList(),listOf(earlier),pending,firstPage).canEdit)
        val all=incidentOptions(listOf(earlier,current))
        assertEquals(current.eventId,preferredLiveIncident(all,earlier.eventId,current.eventId))
    }
    @Test fun ordinaryIncidentSelectionAndClosedVenueHistoryRemainAvailable() {
        val earlier=start(100);val current=start(200);val all=incidentOptions(listOf(earlier,current))
        assertEquals(earlier.eventId,preferredLiveIncident(all,earlier.eventId,null))
        assertEquals(current.eventId,preferredLiveIncident(all,null,null))
        assertEquals("",preferredLiveIncident(all.map {it.copy(closed=true)},null,null))
    }
}
