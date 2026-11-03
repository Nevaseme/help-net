package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.cloud.GroupMunicipality
import io.github.nevaseme.plandmk1.buildings.BuildingMapCoverage
import io.github.nevaseme.plandmk1.buildings.BuildingAssessmentRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class VenueMapPolicyTest {
    @Test fun activeDemoSelectsMapWithoutAnActivityRegion() {
        assertEquals("26100",GroupMunicipality.forDemo("KYOTO",false)?.code)
        assertEquals("13103",GroupMunicipality.forDemo("ACTUAL",false)?.code)
        assertNull(GroupMunicipality.forDemo("KYOTO",true))
        assertTrue(BuildingMapCoverage.intersects(35.665,139.748,35.668,139.751))
        assertFalse(BuildingMapCoverage.intersects(35.2,138.0,35.3,138.1))
    }
    @Test fun additionalPackageLoadsRealToranomonFootprintsWithoutInventingRates() = runTest {
        val repo=BuildingAssessmentRepository(listOf("toranomon-building-assessment/")){File("src/main/assets",it).inputStream()}
        val chunks=repo.metadata().getValue("chunks").jsonArray.map{it.jsonObject}
        val meshes=chunks.filter{it["assetPrefix"]!=null}
        assertTrue(meshes.isNotEmpty())
        val rows=meshes.flatMap{repo.mesh(it.getValue("mesh").jsonPrimitive.content)}
        assertEquals(5033,rows.size)
        assertEquals(4901,rows.count{it.profile==null&&it.status=="unknown"})
        assertEquals(132,rows.count{it.profile=="highrise-zero"&&it.status=="assumed"})
        assertEquals(rows.size,rows.map{it.id}.toSet().size)
    }
}
