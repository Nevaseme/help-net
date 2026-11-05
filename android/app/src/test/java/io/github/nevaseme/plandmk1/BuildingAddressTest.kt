package io.github.nevaseme.plandmk1
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.buildings.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
class BuildingAddressTest {
    @Test fun sharedNormalizationContract() {
        val fixture=Json.parseToJsonElement(javaClass.classLoader!!.getResource("address-search-cases.json")!!.readText()).jsonObject
        for(row in fixture.getValue("normalization").jsonArray){val v=row.jsonArray;assertEquals(v[1].jsonPrimitive.content,normalizeAddress(v[0].jsonPrimitive.content))}
        val rows=fixture.getValue("rows").jsonArray.map {val r=it.jsonArray;AddressCandidate(r[0].jsonPrimitive.content,r[1].jsonPrimitive.content,r[2].jsonPrimitive.double,r[3].jsonPrimitive.double,r[4].jsonPrimitive.content)}
        for(value in fixture.getValue("search").jsonArray){val c=value.jsonObject;assertEquals(c.getValue("ids").jsonArray.map{it.jsonPrimitive.content}.sorted(),searchAddresses(rows,c.getValue("query").jsonPrimitive.content).map{it.id}.sorted())}
    }

    @Test fun normalizedFacilityAndStreetAndAmbiguity() {
        assertEquals("京都タワー",normalizeAddress("京都タワー"))
        val rows=listOf(AddressCandidate("h","京都府京都市中京区上本能寺前町488",135.767742,35.011497,"block"),AddressCandidate("a","中央公園",135.0,35.0,"poi"),AddressCandidate("b","中央公園",139.0,35.0,"poi"))
        assertEquals("h",resolveAddress(rows,"京都市中京区寺町通御池上る上本能寺前町488番地")?.id)
        assertEquals(2,searchAddresses(rows,"中央公園").size)
        assertNull(resolveAddress(rows,"中央公園"))
    }

    @Test fun wardAddressWithNumberFindsCorrectTown() {
        val rows=listOf(AddressCandidate("s","京都府京都市左京区讃州寺町",135.77466,35.010905,"town"),AddressCandidate("k","京都府京都市上京区讃州寺町",135.755382,35.025662,"town"))
        assertEquals("s",resolveAddress(rows,"左京区讃州寺町203")?.id)
        assertEquals("town",resolveAddress(rows,"左京区讃州寺町２０３番地")?.precision)
        assertEquals("s",searchAddresses(rows,"左京区讃州寺町203").single().id)
        assertNull(resolveAddress(rows,"讃州寺町203"));assertNull(resolveAddress(rows,"右京区讃州寺町203"))
    }
    @Test fun legacySerializationOmitsNewFieldsEvenWithDefaultsEnabled() {
        val id=UUID.randomUUID().toString();val e=PlaceEvent("test",id,id,"試験",1,1,PlaceKind.BUILDING,buildingId=id,label="住宅",latitude=35.0,longitude=135.0)
        val json=Json {encodeDefaults=true};assertFalse(json.encodeToString(e).contains("locationReference"))
        val updated=e.copy(locationReference=BuildingLocationReference("京都市中京区試験町","building-id","plateau-26100-kyoto-shi-2025"));updated.validate()
        assertEquals(updated,json.decodeFromString<PlaceEvent>(json.encodeToString(updated)))
    }
    @Test fun addressesKeepTownAndParcelPrecision() {
        assertEquals("京都市12-3",normalizeAddress("京都市 １２番地３号"))
        val rows=listOf(AddressCandidate("a","京都市中京区試験町",135.0,35.0,"town"),AddressCandidate("b","京都市中京区試験町12-3",135.0,35.0,"parcel"))
        assertEquals("b",searchAddresses(rows,"試験町１２番地３号").single().id)
        assertEquals("town",searchAddresses(rows,"京都市中京区試験町999").single().precision)
        assertTrue(searchAddresses(rows,"知らない町").isEmpty())
    }
    @Test fun geocodePreservesPrecisionAndCentersAreas() {
        val rows=listOf(AddressCandidate("a","京都府京都市左京区試験町",135.7,35.0,"town"),AddressCandidate("b","京都府京都市左京区別町",135.8,35.1,"town"),AddressCandidate("c","京都府京都市左京区試験町12-3",135.71,35.01,"parcel"),AddressCandidate("p","京都タワー",135.759,34.987,"poi"))
        assertEquals("c",resolveAddress(rows,"京都市左京区試験町１２番地３号")?.id)
        assertEquals("area",resolveAddress(rows,"左京区")?.precision)
        assertEquals(35.05,resolveAddress(rows,"左京区")!!.latitude,1e-9)
        assertEquals("town",resolveAddress(rows,"京都市左京区試験町999")?.precision)
        assertEquals("p",resolveAddress(rows,"京都タワー")?.id)
        assertNull(resolveAddress(rows,"存在しない架空住所"))
    }
}
