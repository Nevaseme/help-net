package io.github.nevaseme.plandmk1.core
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
class TriagePermissionTest {
 @Test fun grantAndRevokeKeepSavedRecords(){
  val admin=GroupMember(UUID.randomUUID().toString(),"Admin",MemberRole.ADMIN,listOf(UUID.randomUUID().toString()))
  val peer=GroupMember(UUID.randomUUID().toString(),"Peer",MemberRole.MEMBER,listOf(UUID.randomUUID().toString()))
  val roster=MemberRoster(groupId="test",members=listOf(admin,peer))
  val event=ResponseEvent("test",UUID.randomUUID().toString(),peer.deviceIds.single(),1,1,UUID.randomUUID().toString(),ResponseChange.Injury(ResponseTarget(TargetKind.PERSON,UUID.randomUUID().toString()),InjuryState.INJURED))
  assertThrows(IllegalArgumentException::class.java){validateResponseCommand(roster,emptyList(),event)}
  val allowed=roster.copy(revision=1,members=listOf(admin,peer.copy(canTriage=true)),history=listOf(RosterRevision(0,roster.members)))
  validateRosterUpdate(roster,allowed,peer.deviceIds.single());validateResponseCommand(allowed,emptyList(),event)
  val revoked=allowed.copy(revision=2,members=roster.members,history=allowed.history+RosterRevision(1,allowed.members))
  validateRosterUpdate(allowed,revoked,peer.deviceIds.single());assertThrows(IllegalArgumentException::class.java){validateResponseCommand(revoked,listOf(event),event.copy(eventId=UUID.randomUUID().toString(),sequence=2))}
  assertEquals(listOf(event),responseHeads(revoked,listOf(event)))
 }
 @Test fun dateUsesAmericanFormat(){assertEquals("11/05/2026 3:30 PM",usDateTime(java.util.Calendar.getInstance().apply {set(2026,10,5,15,30,0)}.timeInMillis))}
}
