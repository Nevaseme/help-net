package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.buildings.*
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
}
