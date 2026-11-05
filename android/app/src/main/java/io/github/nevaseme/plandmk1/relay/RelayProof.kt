package io.github.nevaseme.plandmk1.relay

import io.github.nevaseme.plandmk1.cloud.CloudRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class RelayProof(@kotlinx.serialization.EncodeDefault val version:Int=1,val keyId:String,val body:String,val signature:String)
@Serializable data class RelayEnvelope(val proof:RelayProof,val publicKeySpki:String) {
    fun id()=sha256(proof.body.toByteArray(Charsets.UTF_8))
}
@Serializable data class RelayPublicKey(val deviceId:String,val keyId:String,val publicKeySpki:String)
@Serializable data class RelayPublicKeys(val keys:List<RelayPublicKey>)
interface RelaySigner {
    val publicKeySpki:String
    fun sign(body:ByteArray):ByteArray
    fun keyId()=sha256(decode64(publicKeySpki))
}
fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).toByteString().hex()
fun encode64(bytes:ByteArray)=bytes.toByteString().base64()
fun decode64(value:String)=requireNotNull(value.decodeBase64()) {"Invalid relay encoding"}.toByteArray()

object RelayProofs {
    private val json=Json {ignoreUnknownKeys=true;encodeDefaults=true}
    fun eligible(record:CloudRecord)=!record.derivedStart() && record.record.toString().toByteArray(Charsets.UTF_8).size<=24_000
    fun create(group:String,device:String,record:CloudRecord,signer:RelaySigner):RelayEnvelope {
        require(eligible(record) && record.owner()==device && record.record["groupId"]?.jsonPrimitive?.content==group && !record.derivedStart())
        val body=buildJsonObject {put("domain","pland-relay-1");put("groupId",group);put("collection",record.collection);put("record",record.record)}.toString()
        require(body.toByteArray(Charsets.UTF_8).size<=25_000)
        return RelayEnvelope(RelayProof(keyId=signer.keyId(),body=body,signature=encode64(signer.sign(body.toByteArray(Charsets.UTF_8)))),signer.publicKeySpki)
    }
    /** Integrity verification only. The server additionally binds this key to the author's membership. */
    fun verify(group:String,envelope:RelayEnvelope):CloudRecord {
        val proof=envelope.proof
        require(proof.version==1 && proof.body.toByteArray(Charsets.UTF_8).size<=25_000)
        require(envelope.publicKeySpki.length<=512 && proof.signature.length<=256)
        val der=decode64(envelope.publicKeySpki)
        require(sha256(der)==proof.keyId)
        val key=KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der)) as ECPublicKey
        require(key.params.curve.field.fieldSize==256)
        require(Signature.getInstance("SHA256withECDSA").run {initVerify(key);update(proof.body.toByteArray(Charsets.UTF_8));verify(decode64(proof.signature))}) {"Invalid relay signature"}
        val body=json.parseToJsonElement(proof.body).jsonObject
        require(body.keys==setOf("domain","groupId","collection","record"))
        require(body["domain"]?.jsonPrimitive?.content=="pland-relay-1" && body["groupId"]?.jsonPrimitive?.content==group)
        val collection=body.getValue("collection").jsonPrimitive.content
        require(collection in setOf("residents","events","incidents","places","positions","memos","quakes","responses","deviceRecords"))
        val record=body.getValue("record").jsonObject
        require(record["groupId"]?.jsonPrimitive?.content==group)
        val revision=if(collection=="quakes"&&record["action"]?.jsonPrimitive?.content=="FORM")record["rosterRevision"]?.jsonPrimitive?.long else null
        return CloudRecord(collection,record,revision,proof).also {require(!it.derivedStart());it.key();it.owner();it.revision()}
    }
}
