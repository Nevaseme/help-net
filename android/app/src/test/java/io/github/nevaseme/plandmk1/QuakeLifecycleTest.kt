package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class QuakeLifecycleTest {
    private fun id() = UUID.randomUUID().toString()
    private val a=id(); private val b=id(); private val c=id(); private val secondA=id()
    private val roster=MemberRoster(groupId="trial", members=listOf(
        GroupMember(a,"班長",MemberRole.ADMIN,listOf(a,secondA)),
        GroupMember(b,"確認B",MemberRole.MEMBER,listOf(b)), GroupMember(c,"確認C",MemberRole.MEMBER,listOf(c))))
    private fun report(actor:String,time:Long=0)=QuakeEvent("trial",id(),actor,time,QuakeAction.REPORT,false,title="地震",area="試験地域")
    private fun trainingStart(actor:String=a,time:Long=0)=QuakeEvent("trial",id(),actor,time,QuakeAction.valueOf("TRAINING_START"),true,title="明示訓練",area="試験地域")

    @Test fun administratorStartsEarthquakeAndMemberCannotBypassReportQuorum(){
        val e=report(a).copy(action=QuakeAction.ADMIN_START)
        validateQuakeCommand(roster,emptyList(),e)
        assertFalse(quakeStates(roster,listOf(e)).single().formation.training)
        assertThrows(IllegalArgumentException::class.java){e.copy(actorDeviceId=b).validate(roster)}
        assertThrows(IllegalArgumentException::class.java){e.copy(training=true).validate(roster)}
        assertThrows(IllegalArgumentException::class.java){validateQuakeCommand(roster,listOf(e),e.copy(eventId=id(),operatedAt=1))}
        assertTrue(quakeStates(roster,listOf(report(a))).isEmpty())
    }
    @Test fun administratorExplicitTrainingStartsWithoutChangingRealOrLegacyReportQuorum() {
        val start=trainingStart()
        validateQuakeCommand(roster,emptyList(),start)
        val state=quakeStates(roster,listOf(start)).single()
        assertEquals(start.eventId,state.formation.incidentId);assertTrue(state.formation.training);assertFalse(state.closed)
        assertTrue(formationCandidates(roster,listOf(start)).isEmpty())
        assertTrue(quakeStates(roster,listOf(report(a))).isEmpty())
        assertTrue(formationCandidates(roster,listOf(report(a))).isEmpty())
        assertTrue(formationCandidates(roster,listOf(report(a).copy(training=true))).isEmpty())
        assertThrows(IllegalArgumentException::class.java) {validateQuakeCommand(roster,listOf(start),trainingStart(time=1))}
    }
    @Test fun explicitTrainingRejectsMemberAndActualDisasterFlag() {
        assertThrows(IllegalArgumentException::class.java) {trainingStart(b).validate(roster)}
        assertThrows(IllegalArgumentException::class.java) {trainingStart().copy(training=false).validate(roster)}
        assertThrows(IllegalArgumentException::class.java) {trainingStart().copy(reportIds=listOf(id())).validate(roster)}
    }
    @Test fun parallelExplicitTrainingAliasesKeepAnswersAndEndAcrossDeliveryOrder() {
        val first=trainingStart(time=10);val parallel=trainingStart(secondA,20)
        val end=QuakeEvent("trial",id(),a,30,QuakeAction.END,true,parallel.eventId)
        val history=listOf(first,parallel,end)
        val state=quakeStates(roster,history).single()
        assertTrue(state.closed);assertEquals(setOf(first.eventId,parallel.eventId),state.incidentIds)
        assertEquals(listOf(state),quakeStates(roster,history.reversed()))
        fun row(e:QuakeEvent)=IncidentEvent("trial",e.eventId,e.eventId,e.actorDeviceId,"管理者",1,e.operatedAt,IncidentKind.START,e.title,e.area,true)
        val answer=IncidentEvent("trial",id(),parallel.eventId,b,"B",1,25,IncidentKind.ANSWER,training=true,safety=Safety.SAFE)
        assertEquals(answer,managedIncidentOptions(listOf(row(first),row(parallel),answer),roster,history).single().answers[b])
        val next=trainingStart(time=40);validateQuakeCommand(roster,history,next)
        assertEquals(listOf(true,false),quakeStates(roster,history+next).map {it.closed})
    }

    @Test fun rosterRoundTripAndDuplicateDeviceRejection() {
        assertEquals(roster,MemberRoster.decode(roster.encode()))
        try { roster.copy(members=roster.members+GroupMember(id(),"重複",MemberRole.MEMBER,listOf(a))).validate();fail() }
        catch (_:IllegalArgumentException) { }
    }
    @Test fun twoDevicesOfOneMemberCannotFormAndTwoPeopleCan() {
        val first=report(a); val duplicate=report(secondA,1);val other=report(b,2)
        assertTrue(formationCandidates(roster,listOf(first,duplicate)).isEmpty())
        val formed=formationCandidates(roster,listOf(first,duplicate,other)).single()
        assertEquals(first.eventId,formed.incidentId)
        assertEquals(0L,formed.firstReportedAt)
    }
    @Test fun ownCancellationBeforeFormationAndForeignCancellationRejected() {
        val first=report(a); val other=report(b,1)
        val cancel=QuakeEvent("trial",id(),a,2,QuakeAction.CANCEL,false,targetId=first.eventId)
        validateQuakeCommand(roster,listOf(first),cancel)
        assertTrue(formationCandidates(roster,listOf(first,other,cancel)).isEmpty())
        try {validateQuakeCommand(roster,listOf(first),cancel.copy(actorDeviceId=b));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun formationSurvivesLateCancellationAndEndSurvivesReordering() {
        val reports=listOf(report(a),report(b,1));val candidate=formationCandidates(roster,reports).single()
        val formed=candidate.event(id(),a,2)
        val cancel=QuakeEvent("trial",id(),b,1,QuakeAction.CANCEL,false,targetId=reports[1].eventId)
        val end=QuakeEvent("trial",id(),a,3,QuakeAction.END,false,targetId=candidate.incidentId)
        validateQuakeCommand(roster,reports+formed,end)
        val events=reports+formed+cancel+end
        assertTrue(quakeStates(roster,events).single().closed)
        assertEquals(quakeStates(roster,events),quakeStates(roster,events.reversed()))
        try { validateQuakeCommand(roster,reports+formed,end.copy(actorDeviceId=b));fail() }catch(_:IllegalArgumentException){}
        try { validateQuakeCommand(roster,reports+formed,cancel);fail() }catch(_:IllegalArgumentException){}
    }
    @Test fun independentlyFormedReportsShareOneIncidentAndEnd() {
        val d=id();val members=roster.copy(members=roster.members+GroupMember(d,"D",MemberRole.MEMBER,listOf(d)))
        val left=listOf(report(a,0),report(b,1));val right=listOf(report(c,100),report(d,101))
        val f1=formationCandidates(members,left).single().event(id(),a,1)
        val f2=formationCandidates(members,right).single().event(id(),c,101)
        val end=QuakeEvent("trial",id(),a,200,QuakeAction.END,false,targetId=f2.targetId)
        val history=left+right+f1+f2+end
        val state=quakeStates(members,history).single()
        assertTrue(state.closed)
        assertEquals(setOf(f1.targetId,f2.targetId),state.incidentIds)
        assertEquals(quakeStates(members,history),quakeStates(members,history.reversed()))
        fun start(r:QuakeEvent)=IncidentEvent("trial",r.eventId,r.eventId,r.actorDeviceId,"班",1,r.operatedAt,IncidentKind.START,"地震","地域",false)
        val answer=IncidentEvent("trial",id(),right.first().eventId,b,"B",1,150,IncidentKind.ANSWER,training=false,safety=Safety.SOS)
        val views=managedIncidentOptions(listOf(start(left.first()),start(right.first()),answer),members,history)
        assertEquals(1,views.size);assertTrue(views.single().closed);assertEquals(answer,views.single().answers[b])
    }
    @Test fun staleLocalFormationCannotReuseCancelledVote() {
        val reports=listOf(report(a),report(b,1));val candidate=formationCandidates(roster,reports).single()
        val cancel=QuakeEvent("trial",id(),a,2,QuakeAction.CANCEL,false,targetId=reports.first().eventId)
        try {validateQuakeCommand(roster,reports+cancel,candidate.event(id(),b,3));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun adminEndKeepsItsOriginalScopeWhenAnEarlierFormationArrives() {
        val early=listOf(report(a,0),report(b,1))
        val middle=listOf(report(a,20*60_000L),report(b,20*60_000L+1))
        val late=listOf(report(a,40*60_000L),report(b,40*60_000L+1))
        fun form(reports:List<QuakeEvent>)=formationCandidates(roster,reports).single().event(id(),a,reports.last().operatedAt)
        val firstHistory=middle+late+form(middle)+form(late)
        val before=quakeStates(roster,firstHistory).single()
        val end=QuakeEvent("trial",id(),a,50*60_000L,QuakeAction.END,false,targetId=late.first().eventId,endedIncidentIds=before.incidentIds.sorted())
        val after=quakeStates(roster,firstHistory+end+early+form(early))
        assertTrue(after.all {it.closed})
        assertEquals(after,quakeStates(roster,(firstHistory+end+early+form(early)).reversed()))
    }
    @Test fun daysApartReportsFormWithoutExpiryAndEndSeparatesFreshReports() {
        val day=24*60*60_000L
        val old=listOf(report(a,0),report(b,day*7))
        val f=formationCandidates(roster,old).single().event(id(),a,day*7)
        val end=QuakeEvent("trial",id(),a,day*8,QuakeAction.END,false,targetId=old.first().eventId)
        val delayed=report(c,day*3)
        val fresh=listOf(report(a,day*9),report(b,day*20))
        val history=old+f+end+delayed+fresh
        val next=formationCandidates(roster,history).single()
        assertEquals(fresh.map {it.eventId},next.reportIds)
        val all=history+next.event(id(),b,day*20)
        val states=quakeStates(roster,all)
        assertEquals(listOf(true,false),states.map {it.closed})
        assertEquals(states,quakeStates(roster,all.reversed()))
        assertTrue(formationCandidates(roster,all+report(c,day*40)).isEmpty())
    }
    @Test fun delayedDuplicateEndDoesNotCloseNextEarthquakeOrReuseOldVotes() {
        val old=listOf(report(a,0),report(b,1));val f=formationCandidates(roster,old).single().event(id(),a,1)
        val end=QuakeEvent("trial",id(),a,2,QuakeAction.END,false,targetId=old.first().eventId)
        val fresh=listOf(report(a,3),report(b,4));val next=formationCandidates(roster,fresh).single().event(id(),a,4)
        val duplicateEnd=end.copy(eventId=id(),operatedAt=10)
        val history=old+f+end+fresh+next+duplicateEnd
        assertEquals(listOf(true,false),quakeStates(roster,history).map {it.closed})
        val mixed=listOf(old.first(),fresh.last())
        val stale=formationCandidates(roster,mixed).single().event(id(),a,4)
        assertEquals(listOf(true),quakeStates(roster,old+f+end+fresh.last()+stale).map {it.closed})
        assertTrue(formationCandidates(roster,old+f+end+fresh.last()+stale).isEmpty())
    }

    @Test fun lateEarlierReportUpdatesElapsedOriginAndKeepsIncidentId() {
        val reports=listOf(report(a,100),report(b,101))
        val f=formationCandidates(roster,reports).single().event(id(),a,101)
        val history=reports+f+report(c,0)
        val state=quakeStates(roster,history).single()
        assertEquals(reports.first().eventId,state.formation.incidentId)
        assertEquals(0L,state.formation.firstReportedAt)
        val start=IncidentEvent("trial",reports.first().eventId,reports.first().eventId,a,"確認班",1,100,IncidentKind.START,"地震","地域",false)
        assertEquals(0L,managedIncidentOptions(listOf(start),roster,history).single().start.createdAt)
    }

}
