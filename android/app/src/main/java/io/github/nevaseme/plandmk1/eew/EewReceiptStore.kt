package io.github.nevaseme.plandmk1.eew

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class EewReceipt(val warning: P2pWarning, val receivedAt: Long)

/** Local receipt history; recording a packet alone never starts or closes a disaster. */
class EewReceiptStore(private val directory: File) {
    @Serializable private data class Stored(val source: String = "p2pquake", val raw: String, val receivedAt: Long)

    @Synchronized fun save(warning: P2pWarning, receivedAt: Long): Boolean {
        require(receivedAt >= 0)
        check(directory.isDirectory || directory.mkdirs()) { "速報の保存先を作成できません" }
        // Include test/live to prevent a test packet masking a real message with the same ID.
        val key = "${warning.test}:${warning.messageId}"
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val target = File(directory, "$hash.json")
        if (target.exists()) {
            check(read(target).warning == warning) { "同じ速報IDの内容が異なります" }
            return false
        }
        val pending = File(directory, "$hash.tmp")
        FileOutputStream(pending).use {
            it.write(Json.encodeToString(Stored(source = warning.source, raw = warning.raw, receivedAt = receivedAt)).toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        check(pending.renameTo(target)) { "速報を保存できません" }
        return true
    }

    @Synchronized fun receipts(): List<EewReceipt> =
        directory.listFiles { file -> file.extension == "json" }?.map(::read)
            ?.sortedBy { it.receivedAt }.orEmpty()

    @Synchronized fun latest(eventId: String, test: Boolean = false, source: String = "p2pquake"): EewReceipt? = receipts()
        .filter { it.warning.eventId == eventId && it.warning.test == test && it.warning.source == source }
        .maxWithOrNull(compareBy<EewReceipt> { it.warning.serial }.thenBy { it.warning.issuedAt }
            .thenBy { it.warning.cancelled }.thenBy { it.warning.messageId })

    private fun read(file: File): EewReceipt {
        val stored = Json.decodeFromString<Stored>(file.readText(Charsets.UTF_8))
        check(stored.source in setOf("p2pquake", "wolfx") && stored.receivedAt >= 0)
        return EewReceipt(checkNotNull(EewWarning.parse(stored.raw, stored.source)) { "保存済み速報を読み取れません" }, stored.receivedAt)
    }
}
