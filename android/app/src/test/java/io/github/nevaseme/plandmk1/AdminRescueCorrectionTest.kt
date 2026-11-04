package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AdminRescueCorrectionTest {
    private val roster=MemberRoster(groupId="g",members=listOf(GroupMember("admin-member","Admin",MemberRole.ADMIN,listOf("admin")),GroupMember("leader-member","Leader",MemberRole.LEADER,listOf("leader"))))
    private val rescue=CareEvent("g","rescue","person","rescuer","Rescuer",20,CareAction.RESCUED)
    private val revoke=rescue.copy(eventId="revoke",authorId="admin",authorName="Admin",createdAt=30,action=CareAction.REVOKED,supersedes="rescue")
    @Test fun onlyTrustedSameGroupAdminMayRevokeAnotherAuthorsRescue() {
        assertEquals(listOf(revoke),currentCareEvents(listOf(rescue,revoke),roster))
        for(r in listOf(null,roster.copy(groupId="other")))assertEquals(2,currentCareEvents(listOf(rescue,revoke),r).size)
        assertEquals(2,currentCareEvents(listOf(rescue,revoke.copy(authorId="leader")),roster).size)
        assertEquals(2,currentCareEvents(listOf(rescue,revoke.copy(subjectId="other")),roster).size)
        assertEquals(2,currentCareEvents(listOf(rescue,revoke.copy(action=CareAction.NEEDS_HELP)),roster).size)
        assertEquals(2,currentCareEvents(listOf(rescue.copy(action=CareAction.CHECKING),revoke),roster).size)
    }
    @Test fun adminCorrectionRestoresUnderlyingSafetyAndPreservesSignedHistory() {
        val serialized=Json.encodeToString(rescue)
        val resident=Resident("g","person",1,10,"Name",35.0,135.0,"2","Room",Safety.SOS)
        val history=listOf(rescue,revoke)
        assertEquals(MapSafety.RESCUED,residentMapState(resident,listOf(rescue),roster))
        assertEquals(MapSafety.SOS,residentMapState(resident,history,roster))
        assertNull(residentMapPerson(resident,emptyList(),history,roster=roster).rescuedAt)
        assertEquals(serialized,Json.encodeToString(history.first()))
        assertEquals(2,history.size)
    }
    @Test fun oneUndoRevokesAllConcurrentRescueHeadsAndKeepsOtherPeopleAndHistory() {
        val second=rescue.copy(eventId="rescue-2",authorId="other-rescuer",createdAt=21)
        val other=rescue.copy(eventId="other-person-rescue",subjectId="other-person")
        val history=listOf(rescue,second,other)
        val before=Json.encodeToString(history)
        val targets=rescueRecordsToRevoke(history,roster,"g","person","admin")
        assertEquals(setOf("rescue","rescue-2"),targets.map {it.eventId}.toSet())
        val corrections=targets.mapIndexed {i,e->revoke.copy(eventId="revoke-$i",supersedes=e.eventId)}
        val combined=history+corrections
        val resident=Resident("g","person",1,10,"Name",35.0,135.0,"2","Room",Safety.SOS)
        assertEquals(MapSafety.SOS,residentMapState(resident,combined,roster))
        assertTrue(rescueRecordsToRevoke(combined,roster,"g","person","admin").isEmpty())
        assertEquals(listOf(other),currentCareEvents(combined,roster).filter {it.action==CareAction.RESCUED})
        assertEquals(before,Json.encodeToString(history))
        assertEquals("Name",publicRecordAuthor("person",listOf(resident),combined,roster))
        assertFalse(careSummary(combined,"person",roster).contains("本人の救助完了"))
        assertThrows(IllegalArgumentException::class.java){rescueRecordsToRevoke(history,roster,"g","person","leader")}
    }
}
