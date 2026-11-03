package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.cloud.CloudRosterEnvelope
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class VenueModeTest {
    @Test fun changingDemoHomeDoesNotMovePhonePosition() {
        val home=io.github.nevaseme.plandmk1.core.DemoHome("building","Changed home",35.1,135.1,null)
        val placement=io.github.nevaseme.plandmk1.demo.DemoPlacement("s","device","member","Demo",0,35.0,135.0)
        val venue=io.github.nevaseme.plandmk1.demo.VenueSession(groupId="g",sessionId="s",incidentId="s",areaId="kyoto-gosho-south",placements=listOf(placement))
        val edited=venue.withHomes(listOf(io.github.nevaseme.plandmk1.core.DeviceRecord("g","record","device",1,1000,"DEMO_HOME",sessionId="s",home=home)))
        assertEquals(35.0,edited.placements.single().latitude!!,0.0)
        assertEquals(135.0,edited.placements.single().longitude!!,0.0)
        assertEquals(home,edited.placements.single().home)
        assertEquals(35.1,edited.placeRecords().first {it.kind==io.github.nevaseme.plandmk1.core.PlaceKind.BUILDING}.latitude!!,0.0)
    }
    @Test fun oldScenariosGainOnlyFictionalObservationsAndSafePeopleHideThem() {
        val home=io.github.nevaseme.plandmk1.core.DemoHome("building","Demo address",35.0,135.0,null)
        val people=(0..11).map {io.github.nevaseme.plandmk1.demo.ScenarioPerson("p$it","Demo $it","UNREPORTED","UNKNOWN","","west",home)}
        val venue=io.github.nevaseme.plandmk1.demo.VenueSession(groupId="g",sessionId="s",incidentId="s",areaId="kyoto-gosho-south",placements=emptyList(),startedAt=1000,scenario=io.github.nevaseme.plandmk1.demo.VenueScenario(sessionId="s",communities=emptyList(),people=people))
        assertEquals(1000L to 1000L,venue.observationTimes("p6"))
        assertEquals(null to 1000L,venue.observationTimes("p5"))
        assertEquals(null to null,venue.observationTimes("real participant"))
        val p=io.github.nevaseme.plandmk1.ui.MapPerson("p6","Demo",io.github.nevaseme.plandmk1.ui.MapSafety.UNANSWERED,demo=true,fictional=true,impactAt=1000,enclosedAt=1000)
        assertEquals(1000L,io.github.nevaseme.plandmk1.ui.projectPersonPrivacy(p,0,2000).enclosedAt)
        assertNull(io.github.nevaseme.plandmk1.ui.projectPersonPrivacy(p.copy(state=io.github.nevaseme.plandmk1.ui.MapSafety.SAFE),0,2000).enclosedAt)
    }
    @Test fun actualModeDoesNotTurnUnknownLocationIntoKyotoOrHome() {
        val venue=io.github.nevaseme.plandmk1.demo.VenueSession(groupId="group",sessionId="demo",incidentId="demo",areaId="kyoto-gosho-south",positionMode="ACTUAL",placements=listOf(io.github.nevaseme.plandmk1.demo.DemoPlacement("demo","device","member","参加者",0,null,null,"DEVICE_LOCATION")))
        val person=venue.displayResidents().single();assertTrue(person.latitude.isNaN());assertTrue(person.longitude.isNaN())
        val mapped=io.github.nevaseme.plandmk1.ui.residentMapPerson(person,emptyList(),emptyList())
        assertNull(mapped.point)
        val visible=io.github.nevaseme.plandmk1.ui.projectPersonPrivacy(mapped.copy(demo=true,home=null),0,1);assertNull(visible.point);assertNull(visible.home)
    }
    @Test fun assignmentsApplyOnlyToTheirOwnActiveVenueAndNeverAsHomes() {
        val venue=io.github.nevaseme.plandmk1.demo.VenueSession(groupId="group",sessionId="demo",incidentId="demo",areaId="kyoto-gosho-south",placements=listOf(io.github.nevaseme.plandmk1.demo.DemoPlacement("demo","device","member","参加者",0,35.014,135.762)))
        assertTrue(venue.active("group","demo"))
        assertFalse(venue.active("other","demo"))
        assertFalse(venue.active("group","real-earthquake"))
        assertFalse(venue.active("group","ordinary-training"))
        assertFalse(venue.copy(closed=true).active("group","demo"))
        val rendered=venue.displayResidents().single()
        assertEquals("",rendered.floor);assertEquals("",rendered.room)
        assertEquals(35.014,rendered.latitude,0.0)
        val person=io.github.nevaseme.plandmk1.ui.MapPerson("device","参加者",io.github.nevaseme.plandmk1.ui.MapSafety.SAFE,demo=true)
        val safe=io.github.nevaseme.plandmk1.ui.projectPersonPrivacy(person,0,1)
        assertEquals("参加者",safe.publicName);assertNull(safe.home);assertFalse(safe.registeredPlaceVisible)
    }
    @Test fun venueMetadataFromServerSurvivesAndroidDecodingForOfflineUse() {
        val group="00000000-0000-0000-0000-000000000001"
        val session="00000000-0000-0000-0000-000000000002"
        val json=Json {ignoreUnknownKeys=true;encodeDefaults=true}
        val body="""{"groupId":"$group","rosterRevision":1,"roster":{"version":1,"groupId":"$group","members":[]},"rosterHistory":[],"venue":{"version":1,"mode":"VENUE_DEMO","groupId":"$group","sessionId":"$session","incidentId":"$session","areaId":"kyoto-gosho-south","areaName":"御所南〜烏丸御池","closed":false,"placements":[]}}"""
        val decoded=json.decodeFromString<CloudRosterEnvelope>(body)
        val encoded=json.encodeToJsonElement(decoded).jsonObject
        assertTrue("Venue metadata must not be silently discarded",encoded["venue"] is JsonObject)
        assertEquals(session,encoded.getValue("venue").jsonObject["incidentId"]!!.jsonPrimitive.content)
    }
}
