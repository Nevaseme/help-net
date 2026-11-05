package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CloudRosterTest {
    private fun id()=UUID.randomUUID().toString()
    private fun member(admin:Boolean=false)=id().let {GroupMember(it,"架空参加者",if(admin)MemberRole.ADMIN else MemberRole.MEMBER,listOf(it))}
    private val initial=MemberRoster(groupId="trial",members=listOf(member(true),member()))
    private fun reports()=initial.members.mapIndexed {i,m->QuakeEvent("trial",id(),m.deviceIds.single(),i.toLong(),QuakeAction.REPORT,false,title="地震",area="地域")}
    @Test fun recoveryAddsMemberDeviceWithoutChangingAuthorityOrHistory() {
        val device=id();val next=initial.copy(revision=1,members=initial.members.map {if(it.role==MemberRole.MEMBER)it.copy(deviceIds=it.deviceIds+device)else it},history=listOf(RosterRevision(0,initial.members)))
        validateRosterUpdate(initial,next,device)
        assertEquals(initial.members.size,next.members.size)
        assertThrows(IllegalArgumentException::class.java){validateRosterUpdate(initial,next.copy(members=initial.members.map {if(it.role==MemberRole.ADMIN)it.copy(deviceIds=it.deviceIds+device)else it}),device)}
    }
    @Test fun joiningMembersDoesNotInvalidateHistoricalFormation() {
        val old=initial.copy(revision=1)
        val reports=reports();val formation=formationCandidates(old,reports).single().event(id(),old.members.first().deviceIds.first(),2)
        assertEquals(1L,formation.rosterRevision)
        val current=initial.copy(revision=2,members=initial.members+List(5){member()},history=listOf(RosterRevision(1,initial.members)))
        current.validate();assertTrue(quakeStates(current,reports+formation).single().closed.not())
        assertTrue(formationCandidates(current,reports).isEmpty())
    }
    @Test fun unknownRevisionCannotFormAndLegacyBaselineRemainsUsable() {
        val reports=reports();val legacy=formationCandidates(initial,reports).single().event(id(),initial.members.first().deviceIds.first(),2)
        val current=initial.copy(revision=1,members=initial.members+List(5){member()},history=listOf(RosterRevision(0,initial.members)))
        assertEquals(1,quakeStates(current,reports+legacy).size)
        assertTrue(quakeStates(current,reports+legacy.copy(rosterRevision=99)).isEmpty())
    }
    @Test fun revisionUpdateIsAppendOnlyAndKnownSnapshotsCannotChange() {
        val next=initial.copy(revision=1,members=initial.members+member(),history=listOf(RosterRevision(0,initial.members)))
        validateRosterUpdate(initial,next,initial.members.first().deviceIds.first())
        assertThrows(IllegalArgumentException::class.java){validateRosterUpdate(next,initial,initial.members.first().deviceIds.first())}
        assertThrows(IllegalArgumentException::class.java){validateRosterUpdate(initial,next.copy(members=next.members.mapIndexed {i,m->if(i==0)m.copy(role=MemberRole.MEMBER) else m}),initial.members.first().deviceIds.first())}
        assertThrows(IllegalArgumentException::class.java){validateRosterUpdate(initial,next.copy(history=listOf(RosterRevision(0,next.members))),initial.members.first().deviceIds.first())}
    }
}
