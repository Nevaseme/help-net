package io.github.nevaseme.plandmk1.buildings

import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Published land parcel correspondence; the user must confirm the home address. */
data class ParcelAddressChoice(val parcelId:String,val address:String,val status:String,val latitude:Double?,val longitude:Double?,val source:String,val sourceVersion:String)

class ParcelAddressRepository(private val openAsset:(String)->InputStream) {
    private val json=Json {ignoreUnknownKeys=true}
    private val mutex=Mutex()
    private var index:JsonObject?=null
    private val chunks=linkedMapOf<String,JsonObject>()
    private val prefix="kyoto-parcel-addresses/"
    suspend fun choices(id:String,latitude:Double,longitude:Double):List<ParcelAddressChoice> = withContext(Dispatchers.IO) { mutex.withLock {
        if(!id.startsWith("bldg_")||longitude !in 135.0..136.0||latitude !in 34.5..35.5)return@withLock emptyList()
        val idx=index?:json.parseToJsonElement(openAsset(prefix+"index.json").use {it.readBytes().decodeToString()}).jsonObject.also {
            require(it["schemaVersion"]?.jsonPrimitive?.int==1);index=it
        }
        val source=idx.getValue("source").jsonObject
        for(entry in idx.getValue("chunks").jsonArray){
            val c=entry.jsonObject;val b=c.getValue("bbox").jsonArray.map{it.jsonPrimitive.double}
            if(longitude<b[0]||latitude<b[1]||longitude>b[2]||latitude>b[3])continue
            val mesh=c.getValue("mesh").jsonPrimitive.content
            val body=chunks[mesh]?:run {
                val bytes=openAsset(prefix+c.getValue("file").jsonPrimitive.content).use{it.readBytes()}
                val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
                require(hash==c.getValue("sha256").jsonPrimitive.content){"住所候補のデータが一致しません"}
                val parsed=json.parseToJsonElement(GZIPInputStream(bytes.inputStream()).bufferedReader().use{it.readText()}).jsonObject
                require(parsed["schemaVersion"]?.jsonPrimitive?.int==1&&parsed["mesh"]?.jsonPrimitive?.content==mesh)
                if(chunks.size>=2)chunks.remove(chunks.keys.first());chunks[mesh]=parsed;parsed
            }
            val link=body.getValue("buildings").jsonObject[id]?.jsonObject?:continue
            return@withLock link.getValue("candidates").jsonArray.filter{it.jsonObject["numbered"]?.jsonPrimitive?.booleanOrNull!=false}.map {row->val r=row.jsonObject
                ParcelAddressChoice(r.getValue("parcelId").jsonPrimitive.content,r.getValue("address").jsonPrimitive.content,link.getValue("status").jsonPrimitive.content,
                    r["latitude"]?.jsonPrimitive?.doubleOrNull,r["longitude"]?.jsonPrimitive?.doubleOrNull,
                    source.getValue("id").jsonPrimitive.content,source.getValue("version").jsonPrimitive.content)
            }.filter{it.address.isNotBlank()}.distinctBy{it.address}
        }
        emptyList()
    }}
}
