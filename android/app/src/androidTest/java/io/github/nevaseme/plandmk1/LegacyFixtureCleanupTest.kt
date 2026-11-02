package io.github.nevaseme.plandmk1

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.PhonePosition
import io.github.nevaseme.plandmk1.core.Resident
import io.github.nevaseme.plandmk1.data.PhonePositionRow
import io.github.nevaseme.plandmk1.data.ResidentRow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** One-time, user-requested removal of exactly the two diagnosed 2026-10-31 offline fixture updates. */
class LegacyFixtureCleanupTest {
    @Test fun discardOnlyDiagnosedOfflineUpdates() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("discardLegacyFixture") == "2026-10-31-two-records")
        val app = ApplicationProvider.getApplicationContext<PlanDApplication>()
        val group = app.installation.group.value
        val settings = CloudSettings(app)
        val session = requireNotNull(settings.session(group))
        check(session.groupName == "Demo-1031")
        val checkpoint = settings.checkpoint(session)
        val journal = RoomCloudJournal(app.database, group, session.deviceId)
        val before = journal.records()
        val pending = before.filter { !it.derivedStart() && !checkpoint.acknowledged(it) }
        check(pending.size == 2 && pending.all { it.owner() != session.deviceId && it.relayProof == null })
        val resident = pending.single { it.collection == "residents" }
        val position = pending.single { it.collection == "positions" }
        check(resident.revision() == 5L && resident.record["room"]?.jsonPrimitive?.content == "SF-1522-X-OFFLINE")
        check(resident.record["updatedAt"]?.jsonPrimitive?.long == 1793414327501L)
        check(position.revision() == 85L && position.owner() == resident.owner())
        check(position.record["acquiredAt"]?.jsonPrimitive?.long == 1793414745435L)
        val api = CloudHttpApi()
        val server = mutableListOf<CloudRecord>()
        var cursor = 0L
        do {
            val page = api.pull(session, cursor)
            server += page.records.map { it.value() }
            cursor = page.nextCursor
        } while (page.hasMore)
        fun remote(row: CloudRecord) = server.filter { it.collection == row.collection && it.key() == row.key() }.maxBy { it.revision() }
        val savedResident = remote(resident)
        val savedPosition = remote(position)
        check(savedResident.revision() == 4L && savedPosition.revision() == 81L)
        check(checkpoint.acknowledged(savedResident) && checkpoint.acknowledged(savedPosition))
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restoredResident = json.decodeFromJsonElement<Resident>(savedResident.record).also { it.validate() }
        val restoredPosition = json.decodeFromJsonElement<PhonePosition>(savedPosition.record).also { it.validate() }
        val backup = File(app.filesDir, "maintenance/legacy-offline-20261031-${System.currentTimeMillis()}.json")
        backup.parentFile!!.mkdirs()
        backup.writeText(json.encodeToString(pending))
        check(json.decodeFromString<List<CloudRecord>>(backup.readText()) == pending)
        fun isTarget(row: CloudRecord) = pending.any { it.collection == row.collection && it.key() == row.key() }
        app.database.withTransaction {
            val current = journal.records()
            check(current.filter(::isTarget).toSet() == pending.toSet())
            app.database.residents().put(ResidentRow.from(restoredResident))
            app.database.localSituation().putPosition(PhonePositionRow(restoredPosition))
            val after = journal.records()
            check(after.filterNot(::isTarget).toSet() == current.filterNot(::isTarget).toSet())
            check(after.size == current.size)
            check(after.filter(::isTarget).all { checkpoint.acknowledged(it) })
        }
        check(app.installation.group.value == group && settings.session(group) == session)
        val remaining = journal.records().count { !it.derivedStart() && !checkpoint.acknowledged(it) }
        File(app.getExternalFilesDir(null), "legacy-cleanup-result.json").writeText(buildJsonObject {
            put("discardedLocalUpdates", 2)
            put("restoredServerRecords", 2)
            put("unrelatedRecordsUnchanged", true)
            put("groupAndMembershipUnchanged", true)
            put("remainingPending", remaining)
            put("recoveryCopyKeptOnDevice", true)
        }.toString())
    }
}
