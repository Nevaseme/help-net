package io.github.nevaseme.plandmk1

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.relay.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit opt-in, read-only inspection. Exports counts/field names, never credentials or record bodies. */
class CloudPendingDiagnosticTest {
    @Test fun inspectPendingMetadata() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("inspectCurrentGroup") == "true")
        val app = ApplicationProvider.getApplicationContext<PlanDApplication>()
        val group = app.installation.group.value
        val settings = CloudSettings(app)
        val session = requireNotNull(settings.session(group))
        val checkpoint = settings.checkpoint(session)
        val pending = RoomCloudJournal(app.database, group, session.deviceId).records()
            .filter { !it.derivedStart() && !checkpoint.acknowledged(it) }
        val proofs = app.database.relayProofs().all(group).associateBy { it.identity }
        val api = CloudHttpApi()
        val roster = api.roster(session)
        val keys = api.relayKeys(session)
        val server = mutableListOf<CloudRecord>()
        var cursor = 0L
        do {
            val page = api.pull(session, cursor)
            server += page.records.map { it.value() }
            cursor = page.nextCursor
        } while (page.hasMore)
        val json = Json { ignoreUnknownKeys = true }
        val report = buildJsonObject {
            put("pendingCount", pending.size)
            put("checkpointCursor", checkpoint.cursor())
            put("serverCursor", cursor)
            put("rows", buildJsonArray {
                for (row in pending) add(buildJsonObject {
                    put("collection", row.collection)
                    put("revision", row.revision())
                    put("own", row.owner() == session.deviceId)
                    put("ownerInRoster", roster.roster.members.any { row.owner() in it.deviceIds })
                    put("ownerHasRegisteredRelayKey", keys.keys.any { it.deviceId == row.owner() })
                    put("matchesOfflineXiaomiFixture", row.record["room"]?.jsonPrimitive?.content == "SF-1522-X-OFFLINE")
                    for (field in listOf("updatedAt", "acquiredAt")) row.record[field]?.let { put(field, it) }
                    put("attachedProof", row.relayProof != null)
                    val stored = proofs["${row.collection}:${row.key()}"]
                    put("storedProof", stored != null)
                    if (stored != null) {
                        val signed = RelayProofs.verify(group, json.decodeFromString<RelayEnvelope>(stored.payload))
                        put("proofRevision", signed.revision())
                        put("proofDifferentFields", JsonArray((signed.record.keys + row.record.keys)
                            .filter { signed.record[it] != row.record[it] }.map(::JsonPrimitive)))
                    }
                    val remote = server.filter { it.collection == row.collection && it.key() == row.key() }.maxByOrNull { it.revision() }
                    put("existsOnServer", remote != null)
                    if (remote != null) {
                        put("serverRevision", remote.revision())
                        put("serverDifferentFields", JsonArray((remote.record.keys + row.record.keys)
                            .filter { remote.record[it] != row.record[it] }.map(::JsonPrimitive)))
                    }
                    for (field in listOf("kind", "action")) row.record[field]?.let { put(field, it) }
                })
            })
        }
        File(app.getExternalFilesDir(null), "pending-diagnostic.json").writeText(report.toString())
    }
}
