package io.github.nevaseme.plandmk1.cloud

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.SecureRandom
import java.util.UUID

@Serializable data class PendingCloudRegistration(val endpoint:String,val path:String,val body:JsonObject) {
    override fun toString()="PendingCloudRegistration(path=$path)"
}
/** App-private credentials are excluded by the application's existing backup/transfer rules. */
class CloudSettings(context:Context,preferencesName:String="cloud-sharing") {
    private val prefs=context.getSharedPreferences(preferencesName,Context.MODE_PRIVATE)
    private val json=Json {ignoreUnknownKeys=true}
    fun session(group:String):CloudSession?=prefs.getString("session:$group",null)?.let {json.decodeFromString(it)}
    fun venue(group:String):io.github.nevaseme.plandmk1.demo.VenueSession?=prefs.getString("venue:$group",null)?.let {json.decodeFromString(it)}
    fun municipality(group:String):GroupMunicipality?=prefs.getString("municipality:$group",null)?.let {json.decodeFromString(it)}
    fun saveVenue(body:CloudRosterEnvelope) {body.venue?.validate(body.roster);body.municipality?.validate();check(prefs.edit().putString("venue:${body.groupId}",body.venue?.let {json.encodeToString(it)}).putString("municipality:${body.groupId}",body.municipality?.let {json.encodeToString(it)}).commit())}
    fun venueOnboarding(group:String)=prefs.getString("venue-onboarding:$group",null)
    fun setVenueOnboarding(group:String,session:String?) {check(prefs.edit().putString("venue-onboarding:$group",session).commit())}
    fun venueStart(group:String):String=prefs.getString("venue-start:$group",null)?:UUID.randomUUID().toString().also {check(prefs.edit().putString("venue-start:$group",it).commit())}
    fun finishVenueStart(group:String) {check(prefs.edit().remove("venue-start:$group").commit())}
    fun beginVenue(endpoint:String,device:String,name:String,groupName:String):PendingCloudRegistration {
        require(name.isNotBlank()&&name.length<=60&&groupName.isNotBlank()&&groupName.length<=80)
        val base=CloudHttpApi.endpoint(endpoint);val old=pending()
        if(old!=null){require(old.path=="venue/join"&&old.endpoint==base&&old.body["groupName"]?.jsonPrimitive?.content==groupName&&old.body["activityName"]?.jsonPrimitive?.content==name){"前回と同じグループ名・名前で再試行してください"};return old}
        val body=buildJsonObject {put("deviceId",device);put("activityName",name);put("groupName",groupName);put("registrationToken",ByteArray(32).also {SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)})}
        return PendingCloudRegistration(base,"venue/join",body).also {check(prefs.edit().putString("pending",json.encodeToString(it)).commit())}
    }
    fun nearbyKey(group:String):String?=prefs.getString("nearby:$group",null)
    fun beginRecovery(endpoint:String,device:String,groupName:String,code:String):PendingCloudRegistration {
        val base=CloudHttpApi.endpoint(endpoint)
        require(groupName.isNotBlank()&&groupName.length<=80&&code.isNotBlank()&&code.length<=40)
        pending()?.let {old->require(old.path=="venue/recover"&&old.endpoint==base&&old.body["groupName"]?.jsonPrimitive?.content==groupName&&old.body["code"]?.jsonPrimitive?.content==code){"前回と同じ復元情報で再試行してください"};return old}
        val body=buildJsonObject {put("deviceId",device);put("groupName",groupName);put("code",code);put("registrationToken",ByteArray(32).also {SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)})}
        return PendingCloudRegistration(base,"venue/recover",body).also {check(prefs.edit().putString("pending",json.encodeToString(it)).commit())}
    }
    fun saveNearbyKey(group:String,key:String) {require(key.matches(Regex("[a-f0-9]{64}")));check(prefs.edit().putString("nearby:$group",key).commit())}
    fun relayKeyRegistered(group:String)=prefs.getBoolean("relay-key:$group",false)
    fun markRelayKeyRegistered(group:String) {check(prefs.edit().putBoolean("relay-key:$group",true).commit())}
    fun pending():PendingCloudRegistration?=prefs.getString("pending",null)?.let {json.decodeFromString(it)}
    fun begin(endpoint:String,device:String,name:String,groupName:String?=null,groupId:String?=null,code:String?=null):PendingCloudRegistration {
        require(name.isNotBlank()&&name.length<=60)
        require(groupId==null||groupId.matches(Regex("[A-Za-z0-9_-]{1,40}")))
        val base=CloudHttpApi.endpoint(endpoint)
        val old=pending()
        if(old!=null) {
            val expectedPath=if(groupId==null)"groups" else "groups/$groupId/join"
            require(old.endpoint==base&&old.path==expectedPath&&old.body["deviceId"]?.jsonPrimitive?.content==device&&old.body["activityName"]?.jsonPrimitive?.content==name) {"前回のグループ登録を再試行してから変更してください"}
            return old
        }
        val token=ByteArray(32).also {SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)}
        val body=buildJsonObject {
            put("deviceId",device);put("activityName",name);put("registrationToken",token)
            if(groupId==null){require(!groupName.isNullOrBlank());put("requestId",UUID.randomUUID().toString());put("groupName",groupName)}
            else {require(!code.isNullOrBlank());put("code",code)}
        }
        return PendingCloudRegistration(base,if(groupId==null)"groups" else "groups/$groupId/join",body).also {check(prefs.edit().putString("pending",json.encodeToString(it)).commit()) {"グループ登録の再試行情報を保存できません"}}
    }
    fun complete(session:CloudSession) {check(prefs.edit().putString("session:${session.groupId}",json.encodeToString(session)).commit()) {"共有の接続情報を保存できません"}}
    fun cancelRejectedRegistration() {check(prefs.edit().remove("pending").commit())}
    suspend fun submit(pending:PendingCloudRegistration,api:CloudHttpApi):CloudRegistration =try {
        api.register(pending.endpoint,pending.path,pending.body)
    }catch(e:CloudHttpException) {
        if(e.status in setOf(400,403,404,409))cancelRejectedRegistration()
        throw e
    }
    fun selection(session:CloudSession,read:()->String?,write:(String)->Unit):CloudSelection=object:CloudSelection {
        private val key="${session.endpoint}:${session.groupId}:selection-baseline"
        override fun begin(active:Set<String>):Set<String> {
            prefs.getString(key,null)?.let {return json.decodeFromString<Set<String>>(it)}
            check(prefs.edit().putString(key,json.encodeToString(active)).commit())
            return active
        }
        override fun selected()=read()
        override fun select(incident:String)=write(incident)
        override fun finish(){check(prefs.edit().remove(key).commit())}
    }
    fun checkpoint(session:CloudSession):CloudCheckpoint {
        val prefix="${session.endpoint}:${session.groupId}:"
        return object:CloudCheckpoint {
            override fun cursor()=prefs.getLong(prefix+"cursor",0)
            override fun acknowledged(record:CloudRecord)=prefs.getLong(prefix+record.collection+":"+record.key(),0)>=record.revision()
            override fun acknowledge(records:List<CloudRecord>) {commit(null,records)}
            override fun commitPage(cursor:Long,records:List<CloudRecord>) {commit(cursor,records)}
            private fun commit(cursor:Long?,records:List<CloudRecord>) {
                val edit=prefs.edit()
                records.groupBy {prefix+it.collection+":"+it.key()}.forEach {(key,rows)->edit.putLong(key,maxOf(prefs.getLong(key,0),rows.maxOf {it.revision()}))}
                cursor?.let {edit.putLong(prefix+"cursor",it)}
                check(edit.commit()) {"共有の保存確認を端末に記録できません"}
            }
        }
    }
}
