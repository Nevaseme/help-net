package io.github.nevaseme.plandmk1.nearby

import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Group membership proof bound to this Nearby connection, not a reusable radio password. */
class GroupLink(val group: String, val device: String, key: String) {
    private val secret = key.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    init { require(key.matches(Regex("[a-f0-9]{64}"))); require(UUID.fromString(device).toString() == device) }
    val serviceId = "io.github.nevaseme.plandmk1.auto." + MessageDigest.getInstance("SHA-256")
        .digest(group.toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    fun endpoint(name: String) = "pd2|$device|${name.take(24)}"
    fun peer(endpoint: String): Pair<String, String>? {
        val parts = endpoint.split('|', limit=3)
        if (parts.size != 3 || parts[0] != "pd2" || parts[1] == device) return null
        if (runCatching { UUID.fromString(parts[1]).toString() }.getOrNull() != parts[1]) return null
        return parts[1] to parts[2].take(60)
    }
    fun initiates(remoteDevice: String) = device < remoteDevice
    fun proof(rawToken: ByteArray, from: String, to: String): ByteArray {
        val mac=Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret,"HmacSHA256"))
        mac.update("PlanD-group-link-v1\n$group\n$from\n$to\n".toByteArray())
        return mac.doFinal(rawToken)
    }
    fun verifies(rawToken: ByteArray, remote: String, received: ByteArray) =
        MessageDigest.isEqual(proof(rawToken,remote,device),received)
}
