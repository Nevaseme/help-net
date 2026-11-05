package io.github.nevaseme.plandmk1.relay

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** One non-exportable P-256 private key per app installation; only the public key leaves Android. */
class AndroidRelaySigner:RelaySigner {
    private val entry:KeyStore.PrivateKeyEntry by lazy {
        synchronized(AndroidRelaySigner::class.java) {
            val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            if(!store.containsAlias(ALIAS))KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKeyPair()
            store.getEntry(ALIAS,null) as KeyStore.PrivateKeyEntry
        }
    }
    override val publicKeySpki get()=encode64(entry.certificate.publicKey.encoded)
    override fun sign(body:ByteArray)=Signature.getInstance("SHA256withECDSA").run {initSign(entry.privateKey);update(body);sign()}
    companion object {private const val ALIAS="pland-relay-p256-v1"}
}
