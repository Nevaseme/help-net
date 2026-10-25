package io.github.nevaseme.plandmk1.drops

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local-only durable records. A partial write never replaces a completed record. */
class DropEventStore(private val directory: File, private val wallClock: () -> Long = System::currentTimeMillis) {
    private val json = Json { encodeDefaults = true }
    @Synchronized fun save(record: DropRecord) {
        require(record.sessionId.matches(Regex("[0-9a-fA-F-]{36}")))
        require(record.reading.eventElapsedNanos > 0)
        prune()
        if (record.reading.receivedAt <= wallClock() - RETENTION_MILLIS) return
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create drop record directory")
        val destination = File(directory, "${record.sessionId}-${record.reading.eventElapsedNanos}.json")
        if (destination.exists()) {
            check(json.decodeFromString<DropRecord>(destination.readText()) == record) { "Conflicting drop record" }
            return
        }
        val pending = File.createTempFile("pending-", ".tmp", directory)
        try {
            FileOutputStream(pending).use { stream ->
                stream.write(json.encodeToString(record).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            if (!pending.renameTo(destination)) throw IOException("Cannot finish drop record")
        } finally { pending.delete() }
    }

    @Synchronized fun prune() {
        val cutoff = wallClock() - RETENTION_MILLIS
        directory.listFiles()?.forEach { file ->
            val record = if (file.extension == "json") read(file) else null
            val isAbandonedWrite = file.name.matches(Regex("pending-[0-9]+\\.tmp"))
            val isDamagedOwnedRecord = record == null && file.name.matches(Regex("[0-9a-fA-F-]{36}-[0-9]+\\.json"))
            val expired = if (record != null) record.reading.receivedAt <= cutoff
                else (isAbandonedWrite || isDamagedOwnedRecord) && file.lastModified() <= cutoff
            if (expired && !file.delete()) throw IOException("Cannot expire drop record")
        }
    }

    // Preserve malformed files for recovery without allowing one damaged record to block all later events.
    private fun read(file: File): DropRecord? = try { json.decodeFromString<DropRecord>(file.readText()) }
        catch (_: kotlinx.serialization.SerializationException) { null }
        catch (_: IllegalArgumentException) { null }

    @Synchronized fun records(): List<DropRecord> {
        prune()
        return directory.listFiles()?.filter { it.extension == "json" }
            ?.mapNotNull(::read)
            ?.sortedBy { it.reading.receivedAt } ?: emptyList()
    }
    companion object { const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000 }
}
