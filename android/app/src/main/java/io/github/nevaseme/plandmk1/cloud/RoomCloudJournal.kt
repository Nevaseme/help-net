package io.github.nevaseme.plandmk1.cloud

import androidx.room.withTransaction
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.serialization.json.*

class RoomCloudJournal(private val db:PlanDatabase,private val group:String,private val device:String,private val selection:CloudSelection?=null,private val onRoster:(CloudRosterEnvelope)->Unit={},private val allowOwnPosition:()->Boolean={true}):CloudJournal {
    private val store=RoomRecordStore(db,device)
    private val quakes=QuakeStore(db)
    private val json=Json {ignoreUnknownKeys=true;encodeDefaults=true}
    override suspend fun updateRoster(envelope:CloudRosterEnvelope,deviceId:String) {
        require(envelope.groupId==group&&deviceId==device)
        db.withTransaction {quakes.updateRoster(envelope.localRoster(quakes.roster(group),device),device)}
        onRoster(envelope)
    }
    override suspend fun records():List<CloudRecord> =db.withTransaction {
        buildList {
            DeviceRecordStore(db).all(group).forEach {add(CloudRecord("deviceRecords",json.encodeToJsonElement(it).jsonObject))}
            store.all(group).forEach {add(CloudRecord("residents",json.encodeToJsonElement(it).jsonObject))}
            store.events(group).forEach {add(CloudRecord("events",json.encodeToJsonElement(it).jsonObject))}
            store.quakeEvents(group).forEach {add(CloudRecord("quakes",json.encodeToJsonElement(it).jsonObject,if(it.action==QuakeAction.FORM)it.rosterRevision else null))}
            store.incidents(group).filter {it.kind!=IncidentKind.START}.forEach {add(CloudRecord("incidents",json.encodeToJsonElement(it).jsonObject))}
            store.places(group).forEach {add(CloudRecord("places",json.encodeToJsonElement(it).jsonObject))}
            store.positions(group).filter {it.originId!=device||allowOwnPosition()}.forEach {add(CloudRecord("positions",json.encodeToJsonElement(it).jsonObject))}
            store.memos(group).forEach {add(CloudRecord("memos",json.encodeToJsonElement(it).jsonObject))}
            store.responseEvents(group).forEach {add(CloudRecord("responses",json.encodeToJsonElement(it).jsonObject))}
        }.let {records->
            val proofs=db.relayProofs().all(group).associateBy {it.identity}
            records.map {record->
                val stored=proofs["${record.collection}:${record.key()}"]
                val envelope=stored?.let {json.decodeFromString<io.github.nevaseme.plandmk1.relay.RelayEnvelope>(it.payload)}
                if(envelope==null)record else {
                    val signed=io.github.nevaseme.plandmk1.relay.RelayProofs.verify(group,envelope)
                    if(signed.record==record.record)signed else record
                }
            }
        }
    }
    override suspend fun mergePage(records:List<CloudRecord>) {
      val previous=selection?.begin(quakes.states(group).filter {!it.closed&&!it.formation.training}.flatMap {it.incidentIds}.toSet())
      db.withTransaction {
        for(item in records) {
            require(item.record["groupId"]?.jsonPrimitive?.content==group)
            when(item.collection) {
                "deviceRecords"->DeviceRecordStore(db).merge(json.decodeFromJsonElement<DeviceRecord>(item.record))
                "residents"->store.merge(json.decodeFromJsonElement<Resident>(item.record))
                "events"->store.mergeEvent(json.decodeFromJsonElement<CareEvent>(item.record))
                "incidents"->store.mergeIncident(json.decodeFromJsonElement<IncidentEvent>(item.record))
                "places"->store.mergePlace(json.decodeFromJsonElement<PlaceEvent>(item.record))
                "positions"->store.savePosition(json.decodeFromJsonElement<PhonePosition>(item.record))
                "memos"->store.mergeMemo(json.decodeFromJsonElement<SosMemo>(item.record))
                "quakes"->{
                    val event=json.decodeFromJsonElement<QuakeEvent>(item.record)
                    if(event.action==QuakeAction.FORM)require(event.rosterRevision==item.rosterRevision)
                    store.mergeQuake(event)
                }
                "responses"->store.mergeResponse(json.decodeFromJsonElement<ResponseEvent>(item.record))
                else->error("未対応の共有記録です")
            }
        }
      }
      if(selection!=null&&previous!=null) {
        val active=quakes.states(group).filter {!it.closed&&!it.formation.training}
        val selected=selection.selected()
        if(active.none {selected in it.incidentIds}) {
            active.lastOrNull {state->state.incidentIds.none {it in previous}}?.let {selection.select(it.formation.incidentId)}
        }
        selection.finish()
      }
    }
}
