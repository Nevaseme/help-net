package io.github.nevaseme.plandmk1.eew

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@Serializable data class EewTarget(val groupId:String,val deviceId:String)
data class EewReceipt(val warning: P2pWarning, val receivedAt: Long, val target:EewTarget?=null, val actionable:Boolean=false)

/** Local receipt history; recording a packet alone never starts or closes a disaster. */
class EewReceiptStore(private val directory: File) {
    // Defaults deliberately keep pre-connection history non-actionable on upgrade.
    @Serializable private data class Stored(val source: String = "p2pquake", val raw: String, val receivedAt: Long, val target:EewTarget?=null,val actionable:Boolean=false)
    @Serializable data class Applied(val incidentId:String?=null)

    @Synchronized fun save(warning: P2pWarning, receivedAt: Long,target:EewTarget?=null,actionable:Boolean=false): Boolean {
        require(receivedAt >= 0)
        check(directory.isDirectory || directory.mkdirs()) { "速報の保存先を作成できません" }
        // Include test/live to prevent a test packet masking a real message with the same ID.
        val file = file(warning,"json")
        if (file.exists()) {
            check(read(file).warning == warning) { "同じ速報IDの内容が異なります" }
            return false
        }
        writeOnce(file,Json.encodeToString(Stored(source=warning.source,raw=warning.raw,receivedAt=receivedAt,target=target,actionable=actionable&&target!=null)))
        return true
    }

    private fun file(warning:P2pWarning,suffix:String):File {
        // Keep the original receipt filename scheme so existing files remain deduplicated.
        val hash=MessageDigest.getInstance("SHA-256").digest("${warning.test}:${warning.messageId}".toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
        return File(directory,"$hash.$suffix")
    }
    private fun writeOnce(target:File,value:String) {
        if(target.exists())return
        val pending=File(directory,"${target.name}.tmp")
        FileOutputStream(pending).use {
            it.write(value.toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        check(pending.renameTo(target)) { "速報を保存できません" }
    }
    @Synchronized fun receipt(warning:P2pWarning):EewReceipt?=file(warning,"json").takeIf {it.exists()}?.let(::read)
    @Synchronized fun pending(receipt:EewReceipt)=receipt.actionable&&!file(receipt.warning,"done").exists()
    @Synchronized fun applied(receipt:EewReceipt):Applied?=file(receipt.warning,"applied").takeIf {it.exists()}?.let {Json.decodeFromString<Applied>(it.readText())}
    @Synchronized fun markApplied(receipt:EewReceipt,incident:String?) {writeOnce(file(receipt.warning,"applied"),Json.encodeToString(Applied(incident)))}
    @Synchronized fun selected(receipt:EewReceipt)=file(receipt.warning,"selected").exists()
    @Synchronized fun markSelected(receipt:EewReceipt) {writeOnce(file(receipt.warning,"selected"),"selected")}
    @Synchronized fun complete(receipt:EewReceipt) {writeOnce(file(receipt.warning,"done"),"done")}

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
        return EewReceipt(checkNotNull(EewWarning.parse(stored.raw, stored.source)) { "保存済み速報を読み取れません" }, stored.receivedAt,stored.target,stored.actionable)
    }
}
