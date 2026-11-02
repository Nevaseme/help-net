package io.github.nevaseme.plandmk1.cloud

import io.github.nevaseme.plandmk1.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import io.github.nevaseme.plandmk1.demo.VenueSession

@Serializable data class CloudSession(val endpoint:String,val groupId:String,val deviceId:String,val memberId:String,val token:String,val role:MemberRole,val groupName:String="") {
    override fun toString()="CloudSession(groupId=$groupId, role=$role)"
}
@Serializable data class CloudRosterSnapshot(val revision:Long,val roster:MemberRoster)
@Serializable data class CloudRosterEnvelope(val groupId:String,val rosterRevision:Long,val roster:MemberRoster,val rosterHistory:List<CloudRosterSnapshot>,val groupName:String="",val venue:VenueSession?=null,val municipality:GroupMunicipality?=null) {
    fun localRoster(previous:MemberRoster?,deviceId:String):MemberRoster {
        require(rosterRevision>0 && roster.groupId==groupId && rosterHistory.size.toLong()==rosterRevision)
        rosterHistory.forEachIndexed {index,snapshot->require(snapshot.revision==index+1L && snapshot.roster.groupId==groupId);snapshot.roster.validate()}
        require(rosterHistory.last().roster.members==roster.members)
        val legacy=previous?.membersAt(0)?.let {listOf(RosterRevision(0,it))}.orEmpty()
        return roster.copy(revision=rosterRevision,history=legacy+rosterHistory.map {RosterRevision(it.revision,it.roster.members)}).also {validateRosterUpdate(previous,it,deviceId)}
    }
}
@Serializable data class CloudRegistration(val groupId:String,val deviceId:String,val memberId:String,val token:String,val role:MemberRole,val rosterRevision:Long,val roster:MemberRoster,val rosterHistory:List<CloudRosterSnapshot>,val groupName:String="",val venue:VenueSession?=null,val municipality:GroupMunicipality?=null) {
    fun session(endpoint:String)=CloudSession(endpoint,groupId,deviceId,memberId,token,role,groupName)
    fun envelope()=CloudRosterEnvelope(groupId,rosterRevision,roster,rosterHistory,groupName,venue,municipality)
}
@Serializable data class CloudInvitation(val code:String,val expiresAt:Long)
@Serializable data class CloudRecord(val collection:String,val record:JsonObject,val rosterRevision:Long?=null,val relayProof:io.github.nevaseme.plandmk1.relay.RelayProof?=null) {
    fun key()=record.getValue(when(collection){"residents","positions"->"originId";"memos"->"answerId";else->"eventId"}).jsonPrimitive.content
    fun revision()=if(collection in setOf("residents","positions","memos"))record.getValue("revision").jsonPrimitive.long else 1L
    fun owner()=record.getValue(when(collection){"residents","positions"->"originId";"quakes","responses"->"actorDeviceId";else->"authorId"}).jsonPrimitive.content
    fun ackKey()="$collection:${key()}:${revision()}"
    fun derivedStart()=collection=="incidents"&&record["kind"]?.jsonPrimitive?.content=="START"
}
@Serializable data class CloudStoredRecord(val cursor:Long,val collection:String,val record:JsonObject,val rosterRevision:Long) {
    fun value()=CloudRecord(collection,record,rosterRevision)
}
@Serializable data class CloudPage(val records:List<CloudStoredRecord>,val nextCursor:Long,val hasMore:Boolean,val rosterRevision:Long)
@Serializable data class CloudAck(val collection:String,val key:String,val cursor:Long,val status:String,val rosterRevision:Long)
@Serializable data class CloudPushResult(val acks:List<CloudAck>,val rosterRevision:Long)
@Serializable data class CloudPush(val records:List<CloudRecord>)
interface CloudApi {
    suspend fun roster(session:CloudSession):CloudRosterEnvelope
    suspend fun pull(session:CloudSession,after:Long):CloudPage
    suspend fun push(session:CloudSession,records:List<CloudRecord>):CloudPushResult
}
interface CloudJournal {
    suspend fun updateRoster(envelope:CloudRosterEnvelope,deviceId:String)
    suspend fun records():List<CloudRecord>
    /** All rows are merged in one Room transaction. */
    suspend fun mergePage(records:List<CloudRecord>)
}
interface CloudCheckpoint {
    fun cursor():Long
    fun acknowledged(record:CloudRecord):Boolean
    fun acknowledge(records:List<CloudRecord>)
    /** Called only after the page's Room transaction completes; failure leaves replay possible. */
    fun commitPage(cursor:Long,records:List<CloudRecord>)
}
interface CloudSelection {
    /** Persist the pre-page active identities before merging; retries reuse this baseline. */
    fun begin(active:Set<String>):Set<String>
    fun selected():String?
    fun select(incident:String)
    fun finish()
}
data class CloudSyncResult(val uploaded:Int,val downloaded:Int,val unsentForeign:Int,val rejected:Int=0)
