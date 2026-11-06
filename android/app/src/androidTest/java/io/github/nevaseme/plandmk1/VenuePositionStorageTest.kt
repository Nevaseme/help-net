package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import io.github.nevaseme.plandmk1.demo.*
import io.github.nevaseme.plandmk1.location.PositionFix
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Isolated in-memory database; no participant data or OS permissions are changed. */
class VenuePositionStorageTest {
    @Test fun memberWithoutResidentProfileRecordsAssignedPointAndRealTime()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),PlanDatabase::class.java).build()
        try {
            val me=UUID.randomUUID().toString();val session=UUID.randomUUID().toString();val group="position-test"
            QuakeStore(db).importRoster(MemberRoster(groupId=group,members=listOf(GroupMember(me,"Participant",MemberRole.ADMIN,listOf(me)))).encode(),me)
            val store=RoomRecordStore(db)
            assertNull(store.get(group,me))
            val placement=DemoPlacement(session,me,me,"Participant",0,35.0145,135.7625)
            val venue=VenueSession(groupId=group,sessionId=session,incidentId=session,areaId="kyoto",placements=listOf(placement),startedAt=1000)
            val real=PositionFix(35.6665,139.7495,12.0,2345)
            assertTrue(store.recordOwnPosition(group,me,venue.recordingFix(me,real)!!))
            val saved=store.positions(group).single()
            assertEquals(35.0145,saved.latitude,0.0);assertEquals(135.7625,saved.longitude,0.0)
            assertEquals(2345L,venue.assignedPositionTime(placement,saved))
            assertFalse(store.recordOwnPosition(group,me,venue.recordingFix(me,real.copy(acquiredAt=2000))!!))
            try {store.recordOwnPosition(group,UUID.randomUUID().toString(),real);fail("Nonmember accepted")}catch(_:IllegalStateException){}
            assertEquals(listOf(saved),store.positions(group))
        }finally{db.close()}
    }
}
