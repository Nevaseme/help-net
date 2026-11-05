package io.github.nevaseme.plandmk1
import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test
class CommunityOverviewTest {
 @Test fun separatesInjuryFromSosAndRescue(){
  val people=listOf(CommunityPerson("a","Alex","west","RESCUED",true),CommunityPerson("b","Bo","west","SOS"),CommunityPerson("c","Cam","west","SAFE"))
  val rows=communityOverview(people+people.first(),listOf(ResponseChange.Injury(ResponseTarget(TargetKind.PERSON,"a"),InjuryState.INJURED),ResponseChange.Injury(ResponseTarget(TargetKind.PERSON,"c"),InjuryState.UNINJURED)),listOf(CommunityName("west","西","West")))
  assertEquals(3,rows.single().total);assertEquals(1,rows.single().injured);assertEquals(1,rows.single().injuryUnknown);assertEquals(1,rows.single().rescued);assertEquals(1,rows.single().fictional)
 }
 @Test fun ambiguousInjuryIsUnknown(){val target=ResponseTarget(TargetKind.PERSON,"a");assertEquals(InjuryState.UNKNOWN,communityInjury(listOf(ResponseChange.Injury(target,InjuryState.INJURED),ResponseChange.Injury(target,InjuryState.UNINJURED)),"a"))}

 @Test fun cancelledMaterialsDisappearAndDifferentUnitConflictsStaySeparate(){
  val target=ResponseTarget(TargetKind.REPORT,"report")
  val report=ResponseChange.Report("report","水",35.0,135.0,"不足",regionIds=listOf("west"))
  val material=ResponseChange.Material(target,"request","飲料水","本",12.0,6.0)
  val regions=listOf(CommunityName("west","西","West"))
  val cancelled=communityOverview(emptyList(),listOf(report,material.copy(cancelled=true,cancelReason="不要")),regions)
  assertTrue(cancelled.single().materials.isEmpty())
  val competing=communityOverview(emptyList(),listOf(report,material,material.copy(cancelled=true,cancelReason="不要")),regions).single().materials.single()
  assertTrue(competing.conflict);assertEquals(6.0,competing.shortfall,0.0)
  val conflict=communityOverview(emptyList(),listOf(report,material,material.copy(unit="箱",needed=2.0,arranged=1.0)),regions).single().materials
  assertEquals(setOf("本","箱"),conflict.map{it.unit}.toSet());assertTrue(conflict.all{it.conflict})
  assertEquals(6.0,conflict.first{it.unit=="本"}.shortfall,0.0);assertEquals(1.0,conflict.first{it.unit=="箱"}.shortfall,0.0)
 }
}
