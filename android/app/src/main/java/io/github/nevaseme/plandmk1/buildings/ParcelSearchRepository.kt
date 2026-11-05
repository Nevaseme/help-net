package io.github.nevaseme.plandmk1.buildings

import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*

/** Exact town/number lookup, bounded to four compressed city shards in memory.
 * Call from the CityAddresses IO dispatcher and mutex, never the UI thread.
 * Coordinates are land interior points, not verified residential entrances.
 */
class ParcelSearchRepository(private val openAsset:(String)->InputStream) {
    private val prefix="kyoto-parcel-search/"
    private var towns:Map<String,Set<String>>?=null
    private var files:Map<String,JsonObject> = emptyMap()
    private val chunks=linkedMapOf<String,Map<String,List<AddressCandidate>>>()
    private val json=Json { ignoreUnknownKeys=true }

    private fun prepare() {
        if(towns!=null)return
        val index=openAsset(prefix+"index.json").bufferedReader().use {json.parseToJsonElement(it.readText()).jsonObject}
        require(index["schemaVersion"]?.jsonPrimitive?.int==1)
        val aliases=mutableMapOf<String,MutableSet<String>>()
        for(value in index.getValue("towns").jsonArray){
            val row=value.jsonArray
            for(form in addressForms(row[0].jsonPrimitive.content))aliases.getOrPut(form){mutableSetOf()}.add(row[1].jsonPrimitive.content)
        }
        files=index.getValue("chunks").jsonArray.associate {it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject}
        towns=aliases
    }

    fun search(query:String):List<AddressCandidate> {
        val queries=addressQueryForms(query)
        if(queries.none {q->q.any {it in '0'..'9'}})return emptyList()
        prepare()
        val ids=linkedSetOf<String>()
        for(q in queries)for(i in 1 until q.length){
            ids.addAll(towns!![q.substring(0,i)].orEmpty())
        }
        val hits=mutableListOf<AddressCandidate>()
        for(id in ids){
            val exact=chunks.remove(id)?.also {chunks[id]=it}?:run {
                val descriptor=files.getValue(id)
                val bytes=openAsset(prefix+descriptor.getValue("file").jsonPrimitive.content).use {it.readBytes()}
                val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
                require(hash==descriptor.getValue("sha256").jsonPrimitive.content){"地番検索データが一致しません"}
                val body=GZIPInputStream(bytes.inputStream()).bufferedReader().use {json.parseToJsonElement(it.readText()).jsonObject}
                require(body["schemaVersion"]?.jsonPrimitive?.int==1)
                val result=mutableMapOf<String,MutableList<AddressCandidate>>()
                for(value in body.getValue("rows").jsonArray){
                    val r=value.jsonArray;val meta=r[5].jsonObject
                    val candidate=AddressCandidate(r[0].jsonPrimitive.content,r[1].jsonPrimitive.content,
                        r[2].jsonPrimitive.double,r[3].jsonPrimitive.double,r[4].jsonPrimitive.content,
                        r.getOrNull(6)?.jsonPrimitive?.content?:normalizeAddress(r[1].jsonPrimitive.content),
                        meta.getValue("matchLevel").jsonPrimitive.content,meta.getValue("source").jsonPrimitive.content,
                        meta.getValue("sourceVersion").jsonPrimitive.content,"26100")
                    for(form in candidate.forms)result.getOrPut(form){mutableListOf()}.add(candidate)
                }
                if(chunks.size>=4)chunks.remove(chunks.keys.first())
                chunks[id]=result;result
            }
            for(q in queries)hits.addAll(exact[q].orEmpty())
        }
        return hits.distinctBy{it.id}.sortedWith(compareBy({it.address},{it.id})).take(12)
    }
}

fun mergeParcelCandidates(existing:List<AddressCandidate>,parcels:List<AddressCandidate>):List<AddressCandidate> {
    if(parcels.isEmpty())return existing
    val precise=existing.filter {it.precision !in setOf("town","block","area","parcel-area")}
    fun qualified(row:AddressCandidate)=row.search.removePrefix("京都府").removePrefix("京都市")
    val trusted=precise.map(::qualified).toSet()
    return (precise+parcels.filter{qualified(it) !in trusted}).take(12)
}
