package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.buildings.*
import io.github.nevaseme.plandmk1.ui.withBuildingAttributes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class BuildingAssessmentTest {
    private fun repository(): BuildingAssessmentRepository {
        val shape = """{"type":"MultiPolygon","coordinates":[[[[135.0,35.0],[135.01,35.0],[135.01,35.01],[135.0,35.01],[135.0,35.0]],[[135.004,35.004],[135.006,35.004],[135.006,35.006],[135.004,35.006],[135.004,35.004]]]]}"""
        val chunk = """{"mesh":"mesh","buildings":[{"id":"real-id","profile":"w0","status":"estimated","reasons":[],"bbox":[135.0,35.0,135.01,35.01],"geometry":$shape,"attributes":{}},{"id":"unknown-id","profile":null,"status":"unknown","reasons":["unknown-year"],"bbox":[135.0,35.0,135.01,35.01],"geometry":$shape,"attributes":{}}]}"""
        val compressed=ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(chunk.toByteArray()) } }.toByteArray()
        val model="""{"modelId":"test","curves":{"w0":{"full":[${List(21){it}.joinToString()}],"fullHalf":[${List(21){it+20}.joinToString()}]}},"limitations":["test"]}""".toByteArray()
        fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        val index="""{"schemaVersion":1,"modelId":"test","modelSha256":"${hash(model)}","chunks":[{"mesh":"mesh","file":"mesh.json.gz","bbox":[135.0,35.0,135.01,35.01],"sha256":"${hash(compressed)}"}]}"""
        val files=mapOf("index.json" to index.toByteArray(),"model.json" to model,"mesh.json.gz" to compressed)
        return BuildingAssessmentRepository { ByteArrayInputStream(files.getValue(it.substringAfterLast('/'))) }
    }

    @Test fun offlineLookupUsesFootprintAndKeepsUnknownSeparate() = runTest {
        val repo=repository()
        val found=repo.atPoint(35.001,135.001,6.0)
        assertEquals(2,found.size)
        assertEquals(.1,found.first().rates!!.full,1e-8)
        assertEquals(.2,found.first().rates!!.halfOnly,1e-8)
        assertEquals(BuildingRiskLevel.MEDIUM, found.first().riskLevel)
        assertEquals(BuildingRiskLevel.UNKNOWN, found.last().riskLevel)
        assertNull(found.last().rates)
        assertEquals(listOf("unknown-year"),found.last().building.reasons)
        assertTrue(repo.atPoint(35.005,135.005,6.0).isEmpty()) // courtyard hole
        assertTrue(repo.atPoint(34.0,135.0,6.0).isEmpty())
        assertEquals(2,repo.atPoint(35.0,135.0,6.0).size) // exterior boundary
    }

    @Test fun interpolationAndInvalidIntensities() = runTest {
        val repo=repository()
        assertEquals(.105,repo.atPoint(35.001,135.001,6.05).first().rates!!.full,1e-8)
        for (i in listOf(4.0,7.1,Double.NaN)) {
            try { repo.atPoint(35.001,135.001,i); fail("invalid intensity accepted") }
            catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun packageIsBundledAndCanBeReadWithoutNetwork() = runTest {
        val root=java.io.File("src/main/assets/kyoto-building-assessment")
        assertTrue("Real fixed calculation package must be bundled",root.resolve("index.json").isFile)
        val repo=BuildingAssessmentRepository { path -> java.io.File("src/main/assets",path).inputStream() }
        val metadata=repo.metadata()
        assertEquals("26100",metadata["cityCode"]!!.jsonPrimitive.content)
        assertEquals(242,metadata["chunks"]!!.jsonArray.size)
        val buildings=repo.mesh("52354600")
        assertEquals(4528,buildings.size)
        assertTrue(buildings.any{it.status=="estimated"})
        assertTrue(buildings.any{it.status=="unknown" && it.profile==null})
    }
    @Test fun fullDamageClassificationBoundaries() {
        for ((rate, expected) in listOf(
            0.0 to BuildingRiskLevel.LOW,
            Math.nextDown(0.1) to BuildingRiskLevel.LOW,
            0.1 to BuildingRiskLevel.MEDIUM,
            Math.nextDown(0.4) to BuildingRiskLevel.MEDIUM,
            0.4 to BuildingRiskLevel.HIGH,
            1.0 to BuildingRiskLevel.HIGH,
        )) assertEquals(expected, BuildingRiskLevel.fromFullDamageRate(rate))
        assertEquals(BuildingRiskLevel.UNKNOWN, BuildingRiskLevel.fromFullDamageRate(null))
        for (invalid in listOf(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            try { BuildingRiskLevel.fromFullDamageRate(invalid); fail("invalid rate accepted") }
            catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun digitizedGridValuesRemainExactAtClassificationBoundary() = runTest {
        // A table entry at 6.3 must not interpolate just below its exact value.
        assertEquals(0.13, repository().atPoint(35.001,135.001,6.3).first().rates!!.full, 0.0)
    }


    @Test fun registeredLookupUsesIdEvenForCourtyardAndNeverSubstitutesNeighbors() = runTest {
        assertEquals("real-id",repository().registeredBuilding("real-id",35.005,135.005)?.id)
        assertNull(repository().registeredBuilding("missing-id",35.001,135.001))
    }
    @Test fun registeredAttributesPopulateMapHomeWithoutInventingMissingValues() {
        val building=AssessedBuilding("real",Json.parseToJsonElement("""{"year":2012,"floors":3,"structureCode":"603"}""").jsonObject,profile=null,status="unknown",reasons=emptyList())
        val home=io.github.nevaseme.plandmk1.ui.MapHome("registered","住所",MapPoint(35.0,135.0))
        val projected=home.withBuildingAttributes(building)
        assertEquals(2012,projected.built);assertEquals(3,projected.floors);assertEquals("RC造",projected.material)
        val unknown=home.withBuildingAttributes(building.copy(attributes=JsonObject(emptyMap())))
        assertNull(unknown.built);assertNull(unknown.floors);assertEquals("不明",unknown.material)
    }

    @Test fun venueScenarioHomesUseBundledSourceAttributes() = runTest {
        val catalogue=Json.parseToJsonElement(java.io.File("../../cloud-sync/src/venue-buildings.json").readText()).jsonObject.getValue("homes").jsonArray.map{it.jsonObject}.sortedBy{it.getValue("buildingId").jsonPrimitive.content}
        val selected=mutableListOf<JsonObject>()
        repeat(12){i->val region=listOf("demo-west","demo-central","demo-east")[i%3];selected+=catalogue.first{home->selected.none {it.getValue("buildingId")==home.getValue("buildingId")}&&io.github.nevaseme.plandmk1.core.demoCommunityFor(home.getValue("longitude").jsonPrimitive.double,listOf(135.760,135.7644))==region}}
        val repository=BuildingAssessmentRepository {java.io.File("src/main/assets",it).inputStream()}
        val mapped=selected.map {home->
            val lat=home.getValue("latitude").jsonPrimitive.double;val lon=home.getValue("longitude").jsonPrimitive.double
            val source=repository.registeredBuilding(home.getValue("plateauId").jsonPrimitive.content,lat,lon)
            assertNotNull("The registered PLATEAU building must be present in the packaged data",source)
            io.github.nevaseme.plandmk1.ui.MapHome(home.getValue("buildingId").jsonPrimitive.content,"登録住所",MapPoint(lat,lon)).withBuildingAttributes(source!!)
        }
        assertEquals(12,mapped.size)
        selected.zip(mapped).forEach {(catalogueHome,mapHome)->
            val attrs=catalogueHome.getValue("attributes").jsonObject
            assertEquals(attrs["year"]?.jsonPrimitive?.intOrNull,mapHome.built)
            assertEquals(attrs["floors"]?.jsonPrimitive?.intOrNull,mapHome.floors)
            assertEquals(attrs["structureCode"]?.jsonPrimitive?.contentOrNull==null,mapHome.material=="不明")
        }
    }
}
