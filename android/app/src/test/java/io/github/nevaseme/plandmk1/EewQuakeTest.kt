package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class EewQuakeTest {
    private val a=UUID.randomUUID().toString();private val b=UUID.randomUUID().toString()
    private val roster=MemberRoster(groupId="trial",members=listOf(GroupMember(a,"管理者",MemberRole.ADMIN,listOf(a)),GroupMember(b,"受信者",MemberRole.MEMBER,listOf(b))))
    private val incident="ee000001-0020-4000-8000-261030120000"
    private fun evidence(actor:String=b,at:Long=100)=Json.decodeFromString<QuakeEvent>("""{"groupId":"trial","eventId":"${UUID.randomUUID()}","actorDeviceId":"$actor","operatedAt":$at,"action":"EEW","training":false,"targetId":"$incident","eew":{"source":"wolfx","eventId":"20261030120000","issuedAt":$at,"receivedAt":$at}}""")
    @Test fun evidenceFormsWithoutInventingHumanReports() {
        val e=evidence();e.validate(roster)
        val state=quakeStates(roster,listOf(e)).single()
        assertEquals(incident,state.formation.incidentId);assertFalse(state.closed)
        assertTrue(formationCandidates(roster,listOf(e)).isEmpty())
    }
    @Test fun actualEewStillFormsWhileExplicitTrainingIsOpen() {
        val training=QuakeEvent("trial",UUID.randomUUID().toString(),a,50,QuakeAction.TRAINING_START,true,title="明示訓練",area="架空地域")
        val actual=evidence()
        validateQuakeCommand(roster,listOf(training),actual)
        val states=quakeStates(roster,listOf(training,actual))
        assertEquals(2,states.size)
        assertEquals(training.eventId,states.single {it.formation.training}.formation.incidentId)
        assertEquals(incident,states.single {!it.formation.training}.formation.incidentId)
        assertTrue(states.none {it.closed})
        assertTrue(formationCandidates(roster,listOf(training,actual)).isEmpty())
    }
    @Test fun laterReceiptOfSameEarthquakeDoesNotReopenAfterAdminEnd() {
        val e=evidence();val end=QuakeEvent("trial",UUID.randomUUID().toString(),a,200,QuakeAction.END,false,incident)
        validateQuakeCommand(roster,listOf(e),end)
        val later=evidence(a,400)
        assertTrue(quakeStates(roster,listOf(later,end)).single().closed)
        assertEquals(quakeStates(roster,listOf(e,later,end)),quakeStates(roster,listOf(end,later,e)))
        assertThrows(IllegalArgumentException::class.java) {validateQuakeCommand(roster,listOf(e),end.copy(actorDeviceId=b))}
    }
    @Test fun providerSwitchDoesNotCreateANewEarthquakeIdentity() {
        val e=evidence();val switched=evidence(a,300).let {it.copy(eew=it.eew!!.copy(source="p2pquake"))}
        switched.validate(roster)
        val end=QuakeEvent("trial",UUID.randomUUID().toString(),a,200,QuakeAction.END,false,incident)
        assertTrue(quakeStates(roster,listOf(e,end,switched)).single().closed)
    }
}
