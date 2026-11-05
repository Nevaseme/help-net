package io.github.nevaseme.plandmk1.relay

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable data class RelayFrame(val version:Int=11,val groupId:String,val type:String,
    val hashes:List<String> = emptyList(),val hash:String="",val index:Int=0,val count:Int=0,val chunk:String="")

/** Runs on the existing serialized sharing actor. At most eight 240 KB partial proofs are retained. */
class RelayExchange(private val group:String,private val repository:RelayRepository,
    private val send:suspend (String,ByteArray)->Unit,private val onDurableReceipt:suspend ()->Unit={}) {
    private val json=Json {ignoreUnknownKeys=true;encodeDefaults=true}
    private data class Partial(val chunks:Array<ByteArray?>)
    private val partial=linkedMapOf<Pair<String,String>,Partial>()
    suspend fun summary(peer:String) {
        repository.envelopes().map {it.id()}.chunked(100).forEach {emit(peer,RelayFrame(groupId=group,type="RELAY_SUMMARY",hashes=it))}
    }
    fun disconnected(peer:String) {partial.keys.removeAll {it.first==peer}}
    suspend fun receive(peer:String,bytes:ByteArray) {
        require(bytes.size<=24000)
        val frame=json.decodeFromString<RelayFrame>(bytes.decodeToString(throwOnInvalidSequence=true))
        require(frame.version in listOf(11,13,14,15) && frame.groupId==group && frame.hashes.size<=100)
        require(frame.hashes.all {it.matches(HASH)})
        when(frame.type) {
            "RELAY_SUMMARY"->{
                val missing=frame.hashes.filter {repository.get(it)==null}
                if(missing.isNotEmpty())emit(peer,RelayFrame(groupId=group,type="RELAY_REQUEST",hashes=missing))
            }
            "RELAY_REQUEST"->for(hash in frame.hashes) {
                val envelope=repository.get(hash)?:continue
                val body=json.parseToJsonElement(envelope.proof.body).jsonObject
                val record=body["record"]?.jsonObject
                val hasSearch=record?.get("locationReference")?.jsonObject?.containsKey("searchReference")==true||record?.get("home")?.jsonObject?.containsKey("searchReference")==true
                val version=if(hasSearch)15 else if(body["collection"]?.toString()=="\"deviceRecords\"")14 else if(body["record"]?.jsonObject?.containsKey("locationReference")==true)13 else 11
                val payload=json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
                require(payload.size<=240000)
                val count=(payload.size+11999)/12000
                for(index in 0 until count)emit(peer,RelayFrame(version=version,groupId=group,type="RELAY_CHUNK",hash=hash,index=index,count=count,
                    chunk=encode64(payload.copyOfRange(index*12000,minOf(payload.size,(index+1)*12000)))))
            }
            "RELAY_CHUNK"->{
                require(frame.hash.matches(HASH) && frame.count in 1..20 && frame.index in 0 until frame.count && frame.chunk.length<=16000)
                val chunk=decode64(frame.chunk);require(chunk.size in 1..12000)
                if(repository.get(frame.hash)!=null)return
                val key=peer to frame.hash
                var buffer=partial[key]
                if(buffer==null || buffer.chunks.size!=frame.count) {
                    if(partial.size>=8)partial.remove(partial.keys.first())
                    buffer=Partial(arrayOfNulls(frame.count));partial[key]=buffer
                }
                buffer.chunks[frame.index]=chunk
                if(buffer.chunks.all {it!=null}) {
                    partial.remove(key)
                    val payload=ByteArray(buffer.chunks.sumOf {it!!.size});var offset=0
                    for(piece in buffer.chunks){piece!!.copyInto(payload,offset);offset+=piece.size}
                    val envelope=json.decodeFromString<RelayEnvelope>(payload.decodeToString(throwOnInvalidSequence=true))
                    require(envelope.id()==frame.hash)
                    if(repository.accept(envelope))onDurableReceipt()
                }
            }
        }
    }
    private suspend fun emit(peer:String,frame:RelayFrame) {
        val bytes=json.encodeToString(frame).toByteArray(Charsets.UTF_8)
        require(bytes.size<=24000)
        send(peer,bytes)
    }
    companion object {private val HASH=Regex("[a-f0-9]{64}")}
}
