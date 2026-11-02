package io.github.nevaseme.plandmk1.relay

import androidx.room.withTransaction
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface RelayRepository {
    suspend fun envelopes():List<RelayEnvelope>
    suspend fun get(hash:String):RelayEnvelope?
    suspend fun accept(envelope:RelayEnvelope):Boolean
}

class RoomRelayRepository(private val db:PlanDatabase,private val group:String,private val device:String,
    private val signer:RelaySigner,private val allowOwnPosition:()->Boolean={true},private val canSign:()->Boolean={true}):RelayRepository {
    private val json=Json {ignoreUnknownKeys=true;encodeDefaults=true}
    private fun identity(record:CloudRecord)="${record.collection}:${record.key()}"
    private suspend fun save(record:CloudRecord,envelope:RelayEnvelope) {
        db.relayProofs().put(RelayProofRow(group,identity(record),record.revision(),envelope.id(),json.encodeToString(envelope)))
    }
    override suspend fun envelopes():List<RelayEnvelope> =withContext(Dispatchers.IO) {
        db.withTransaction {
            val records=RoomCloudJournal(db,group,device,allowOwnPosition=allowOwnPosition).records()
            if(canSign())for(record in records.filter {it.owner()==device && it.relayProof==null && RelayProofs.eligible(it)}) {
                save(record,RelayProofs.create(group,device,record,signer))
            }
            val current=records.associateBy {identity(it)}
            db.relayProofs().all(group).mapNotNull {row->
                val envelope=json.decodeFromString<RelayEnvelope>(row.payload)
                val record=RelayProofs.verify(group,envelope)
                envelope.takeIf {current[row.identity]?.record==record.record}
            }
        }
    }
    override suspend fun get(hash:String):RelayEnvelope?=withContext(Dispatchers.IO) {
        val envelope=db.relayProofs().byHash(group,hash)?.let {json.decodeFromString<RelayEnvelope>(it.payload)}?:return@withContext null
        val record=RelayProofs.verify(group,envelope)
        if(record.collection=="positions"&&record.owner()==device&&!allowOwnPosition())null else envelope
    }
    override suspend fun accept(envelope:RelayEnvelope):Boolean=withContext(Dispatchers.IO) {
        val record=RelayProofs.verify(group,envelope)
        if(record.owner()==device)return@withContext false
        db.withTransaction {
            if(db.relayProofs().byHash(group,envelope.id())!=null)return@withTransaction false
            // The signed original JSON is retained; it is never re-signed by this relay.
            RoomCloudJournal(db,group,device).mergePage(listOf(record))
            val current=RoomCloudJournal(db,group,device).records().find {identity(it)==identity(record)}
            if(current?.record!=record.record)return@withTransaction false
            save(record,envelope)
            true
        }
    }
}
