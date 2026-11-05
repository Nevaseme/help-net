package io.github.nevaseme.plandmk1
import io.github.nevaseme.plandmk1.ui.buildingAgeAt
import java.util.Calendar
import org.junit.Assert.*
import org.junit.Test
class BuildingAgeTest {
 @Test fun ageAdvancesAtLocalNewYear() {
  val before=Calendar.getInstance().apply {clear();set(2026,Calendar.DECEMBER,31,23,59,59)}.timeInMillis
  val after=Calendar.getInstance().apply {clear();set(2027,Calendar.JANUARY,1,0,0,0)}.timeInMillis
  assertEquals(24,buildingAgeAt(2002,before));assertEquals(25,buildingAgeAt(2002,after));assertNull(buildingAgeAt(null,after))
 }
}
