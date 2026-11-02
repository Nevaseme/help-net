package io.github.nevaseme.plandmk1.cloud

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/** HTTPS journal replication is independent from the debug LAN relay and Nearby roles. */
class CloudSyncEngine(private val session:CloudSession,private val api:CloudApi,private val local:CloudJournal,private val checkpoint:CloudCheckpoint) {
    suspend fun sync():CloudSyncResult {
        var membership=api.roster(session);local.updateRoster(membership,session.deviceId)
        var downloaded=0;var pages=0
        do {
            val after=checkpoint.cursor();val page=api.pull(session,after)
            require(page.records.size<=100 && page.nextCursor>=after && page.rosterRevision>0)
            var last=after
            page.records.forEach {row->
                require(row.cursor>last && row.cursor<=page.nextCursor && row.rosterRevision in 1..page.rosterRevision)
                require(row.record["groupId"]?.jsonPrimitive?.content==session.groupId);last=row.cursor
            }
            require(page.nextCursor==last && (!page.hasMore||page.records.isNotEmpty()))
            if(page.rosterRevision>membership.rosterRevision) {membership=api.roster(session);require(membership.rosterRevision>=page.rosterRevision);local.updateRoster(membership,session.deviceId)}
            val records=page.records.map {it.value()}
            local.mergePage(records)
            checkpoint.commitPage(page.nextCursor,records)
            downloaded+=records.size
        }while(page.hasMore && ++pages<50)
        val pending=local.records().filter {!it.derivedStart()&&!checkpoint.acknowledged(it)}
        val foreign=pending.count {it.owner()!=session.deviceId && it.relayProof==null}
        val own=pending.filter {it.owner()==session.deviceId || it.relayProof!=null}
        var uploaded=0;var rejected=0
        suspend fun send(batch:List<CloudRecord>) {
            val result=api.push(session,batch)
            require(result.acks.size==batch.size && result.acks.map {it.collection to it.key}.distinct().size==batch.size)
            result.acks.forEach {ack->require(ack.cursor>0 && ack.status in setOf("stored","duplicate") && batch.any {it.collection==ack.collection&&it.key()==ack.key})}
            checkpoint.acknowledge(batch);uploaded+=batch.size
        }
        var remaining=own
        while(remaining.isNotEmpty()) {
            val before=uploaded
            val deferred=mutableListOf<Pair<CloudRecord,CloudHttpException>>()
            for(batch in batches(remaining)) {
                try {send(batch)}catch(e:CloudHttpException) {
                    if(e.status !in setOf(400,403,409,413))throw e
                    // One rejected row must not hold unrelated valid records in the same batch.
                    if(batch.size==1)deferred+=batch.single() to e else for(record in batch) {
                        try {send(listOf(record))}catch(one:CloudHttpException) {
                            if(one.status !in setOf(400,403,409,413))throw one
                            deferred+=record to one
                        }
                    }
                }
            }
            if(deferred.isEmpty())break
            if(uploaded==before) {
                // Evidence may arrive from another relay later. Keep the persistent worker alive
                // for those specific dependency failures, while permanent invalid rows stop here.
                deferred.firstOrNull {retryCloudFailure(it.second)}?.let {throw it.second}
                rejected=deferred.size
                break
            }
            // UUID order can put FORM/corrections before their parents. Retry only after a new
            // durable ACK: each progressing pass removes at least one row, bounding this loop.
            remaining=deferred.map {it.first}
        }
        return CloudSyncResult(uploaded,downloaded,foreign,rejected)
    }
    private fun batches(records:List<CloudRecord>):List<List<CloudRecord>> {
        val result=mutableListOf<List<CloudRecord>>();var current=mutableListOf<CloudRecord>();var bytes=0
        for(record in records) {
            val size=Json.encodeToString(record).toByteArray(Charsets.UTF_8).size+2
            require(size<240_000) {"共有記録が送信可能なサイズを超えています"}
            if(current.isNotEmpty()&&(current.size>=100||bytes+size>240_000)){result+=current;current=mutableListOf();bytes=0}
            current+=record;bytes+=size
        }
        if(current.isNotEmpty())result+=current
        return result
    }
}
