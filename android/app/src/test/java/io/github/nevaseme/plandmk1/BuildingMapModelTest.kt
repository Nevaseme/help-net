package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.buildings.BuildingMapModel
import org.junit.Assert.*
import org.junit.Test

class BuildingMapModelTest {
    private val model=BuildingMapModel(mapOf("test" to List(21) { it*5.0 }))
    @Test fun exactAndInterpolatedPercentages() {
        assertEquals(60.0,model.percent("test",6.2)!!,0.0)
        assertEquals(62.5,model.percent("test",6.25)!!,1e-10)
        assertEquals(100.0,model.percent("test",7.0)!!,0.0)
        assertNull(model.percent("?",6.2))
    }
    @Test fun expressionAndDetailsUseSameRate() {
        assertTrue(model.buildingExpression(6.2).contains("60.0"))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidIntensity() {model.percent("test",Double.NaN)}
}
