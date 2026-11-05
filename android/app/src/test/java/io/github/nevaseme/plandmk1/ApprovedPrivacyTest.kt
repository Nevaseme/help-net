package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.ui.*
import org.junit.Assert.*
import org.junit.Test

class ApprovedPrivacyTest {
    private val start=1_000_000L
    private val home=MapHome("home","登録された住所",MapPoint(35.0,135.0))
    private fun person(state:MapSafety,position:Boolean=true)=MapPerson("resident","実名",state,
        point=if(position)MapPoint(35.1,135.1)else null,acquiredAt=if(position)start else null,
        home=home,bedroom="2階 寝室",memo="個人のメモ",impactAt=start,enclosedAt=start)

    @Test fun unansweredIdentityAndHomeOpenAtThreeMinutesWithoutChangingSource(){
        val source=person(MapSafety.UNANSWERED)
        val before=projectPersonPrivacy(source,start,start+179_999)
        assertFalse(before.identityVisible);assertEquals("",before.name);assertNull(before.home);assertEquals("",before.bedroom)
        assertNotNull(before.point);assertNotNull(before.impactAt)
        val after=projectPersonPrivacy(source,start,start+180_000)
        assertTrue(after.identityVisible);assertEquals("実名",after.name);assertEquals(home,after.home)
        assertEquals(home,source.home);assertEquals("2階 寝室",source.bedroom)
    }
    @Test fun unknownUnansweredStillHidesHomeBeforeDisclosure(){
        assertNull(projectPersonPrivacy(person(MapSafety.UNANSWERED,false),start,start+1).home)
        assertNotNull(projectPersonPrivacy(person(MapSafety.UNANSWERED,false),start,start+180_000).home)
    }
    @Test fun sosHomeVisibleOnlyWhenPhoneLocationMissing(){
        val known=projectPersonPrivacy(person(MapSafety.SOS),start,start+200_000)
        assertTrue(known.identityVisible);assertNull(known.home);assertFalse(known.registeredPlaceVisible)
        assertNotNull(projectPersonPrivacy(person(MapSafety.SOS,false),start,start+1).home)
    }
    @Test fun safeAndRescuedKeepAnonymousLabelsAndStripObservations(){
        listOf(MapSafety.SAFE,MapSafety.RESCUED).forEach {state->
            val known=projectPersonPrivacy(person(state),start,start+1)
            assertNotEquals("実名",known.publicName);assertNull(known.home);assertNull(known.impactAt);assertNull(known.enclosedAt)
            for(position in listOf(false,true))for(demo in listOf(false,true)) {
                val projected=projectPersonPrivacy(person(state,position).copy(demo=demo),start,start+1)
                assertNull(projected.home);assertEquals("",projected.bedroom);assertEquals("",projected.memo)
                assertFalse(projected.registeredPlaceVisible);assertFalse(projected.identityVisible)
                assertEquals("",projected.name);assertNotEquals("実名",projected.publicName)
                assertNull(projected.impactAt);assertNull(projected.enclosedAt)
            }
        }
    }
    @Test fun absentOrFutureIncidentNeverRevealsUnansweredIdentity(){
        assertFalse(projectPersonPrivacy(person(MapSafety.UNANSWERED),null,start).identityVisible)
        assertFalse(projectPersonPrivacy(person(MapSafety.UNANSWERED),start+1,start).identityVisible)
    }

    @Test fun alreadyOpenHomeRechecksPrivacyAfterSafetyChanges(){
        val source=person(MapSafety.UNANSWERED)
        val disclosed=projectPersonPrivacy(source,start,start+180_000)
        assertEquals(home,visibleHomeDetail(home.id,source.id,listOf(disclosed),listOf(home),home))
        val safe=projectPersonPrivacy(source.copy(state=MapSafety.SAFE),start,start+180_001)
        assertNull(visibleHomeDetail(home.id,source.id,listOf(safe),emptyList(),home))
    }
}
