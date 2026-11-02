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
}
