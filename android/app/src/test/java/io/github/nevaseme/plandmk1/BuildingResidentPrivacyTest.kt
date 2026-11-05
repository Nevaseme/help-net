package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import org.junit.Assert.*
import org.junit.Test

class BuildingResidentPrivacyTest {
    private val start=1_000_000L
    private val home=MapHome("building","京都府京都市中京区少将井町245-1",MapPoint(35.0,135.0),plateauId="plateau-building")
    private val residents=listOf(Safety.SAFE,Safety.UNREPORTED,Safety.SOS,Safety.SOS).mapIndexed {i,state->Resident("g","p$i",1,start,"秘密の名前$i",35.0,135.0,"2","寝室",state)}
    private val places=listOf(PlaceEvent("g","building","author","Author",1,start,PlaceKind.BUILDING,buildingId="building",label=home.address,latitude=35.0,longitude=135.0,locationReference=BuildingLocationReference(home.address,"plateau-building",null)))+
        residents.map {PlaceEvent("g","home-${it.originId}",it.originId,it.name,1,start,PlaceKind.HOME,buildingId="building")}
    private val care=listOf(CareEvent("g","rescue","p3","author","Author",start+100,CareAction.RESCUED))

    @Test fun totalsIncludeProtectedResidentsButRowsNeverRevealTheirHomeAssociation() {
        val counts=buildingResidentCounts(residents,places,care)
        assertEquals(BuildingResidentCounts(4,1,1,1,1),counts["building"])
        assertEquals(counts["building"],counts["plateau-building"])
        val raw=residents.map {residentMapPerson(it,places,care).copy(point=MapPoint(35.1,135.1),acquiredAt=start,demo=true)}
        val early=raw.map {projectPersonPrivacy(it,start,start+179_999)}
        assertTrue(disclosedBuildingResidents(home,early).isEmpty())
        val disclosed=raw.map {projectPersonPrivacy(it,start,start+180_000)}
        assertEquals(listOf("p1"),disclosedBuildingResidents(home,disclosed).map {it.id})
        assertEquals(listOf("p1"),disclosedBuildingResidents(home.copy(id="plateau-building"),disclosed).map {it.id})
        assertEquals(start+100,disclosed.first {it.id=="p3"}.rescuedAt)
        for(p in disclosed.filter {it.state in setOf(MapSafety.SAFE,MapSafety.RESCUED)}) {
            assertEquals("",p.name);assertNull(p.home);assertEquals("",p.bedroom);assertEquals("",p.memo)
        }
    }
    @Test fun buildingJumpUsesDisclosedPhonePositionAndNeverFallsBackToHome() {
        val p=MapPerson("p","Person",MapSafety.UNANSWERED,point=MapPoint(35.2,135.2),acquiredAt=start,home=home,demo=true)
        assertNull(buildingResidentDestination(projectPersonPrivacy(p,start,start+179_999),start+179_999))
        assertEquals(p.point,buildingResidentDestination(projectPersonPrivacy(p,start,start+180_000),start+180_000))
        assertNull(buildingResidentDestination(p.copy(point=null,acquiredAt=null),start+180_000))
        assertNotEquals(home.point,buildingResidentDestination(p,start+180_000))
    }
    @Test fun demoAddressAliasesAreExactAndStoredInputIsUntouched() {
        assertEquals("245-1 Shoshoicho, Nakagyo-ku, Kyoto, Kyoto Prefecture",demoAddressLabel(home.address,"en"))
        assertEquals(home.address,demoAddressLabel(home.address,"ja"))
        assertEquals(home.address+" note",demoAddressLabel(home.address+" note","en"))
        assertEquals("Private text",demoAddressLabel("Private text","en"))
    }
    @Test fun newScenarioBoundariesDoNotChangeLegacyNeighborhoodMembership() {
        assertEquals("demo-east",demoCommunityFor(135.763))
        assertEquals("demo-central",demoCommunityFor(135.763,listOf(135.760,135.7644)))
        assertEquals("demo-west",demoCommunityFor(135.759,listOf(135.760,135.7644)))
        assertEquals("demo-east",demoCommunityFor(135.765,listOf(135.760,135.7644)))
        assertNull(demoCommunityFor(Double.NaN))
        for(cuts in listOf(emptyList(),listOf(135.76),listOf(135.77,135.76),listOf(Double.NaN,135.77))) {
            assertThrows(IllegalArgumentException::class.java){demoCommunityFor(135.76,cuts)}
        }
    }
}
