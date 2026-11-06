package io.github.nevaseme.plandmk1.cloud

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CloudHttpException(val status:Int,val code:String):IOException(if(code=="client_update_required")"このグループの新しい対応記録を読むには、アプリを最新版へ更新してください。端末内の記録は保持されています。" else "共有サーバーに保存できませんでした（$status / $code）")
class CloudHttpApi(private val client:OkHttpClient=OkHttpClient.Builder().callTimeout(30,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()):CloudApi {
    private val json=Json {ignoreUnknownKeys=true}
    suspend fun leaveGroup(session:CloudSession) {
        val result=json.parseToJsonElement(request(session.endpoint,"groups/${session.groupId}/access",session.token,buildJsonObject {put("action","leave")})).jsonObject
        check(result["left"]?.jsonPrimitive?.booleanOrNull==true&&result["groupId"]?.jsonPrimitive?.content==session.groupId)
    }
    companion object {
        fun endpoint(value:String):String=value.trim().toHttpUrl().also {
            require(it.isHttps && it.username.isEmpty()&&it.password.isEmpty()&&it.query==null&&it.fragment==null&&it.encodedPath=="/") {"HTTPSの共有サーバーアドレスを指定してください"}
        }.toString().trimEnd('/')
    }
    private suspend fun request(base:String,path:String,token:String?=null,body:JsonElement?=null,after:Long?=null):String {
        val url=endpoint(base).toHttpUrl().newBuilder().addPathSegments("api/v1/$path").apply {after?.let {addQueryParameter("after",it.toString());addQueryParameter("limit","100")}}.build()
        val builder=Request.Builder().url(url).header("Accept","application/json").header("X-PlanD-Records-Version","9")
        if(token!=null)builder.header("Authorization","Bearer $token")
        if(body!=null)builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        val call=client.newCall(builder.build())
        return suspendCancellableCoroutine {continuation->
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(IOException("共有サーバーに接続できません。端末内の記録は保持されています",e))}
                override fun onResponse(call:Call,response:Response) {
                    response.use {
                        try {
                            val source=response.body?.source()?:error("共有サーバーから応答がありません")
                            val buffer=okio.Buffer();var total=0L
                            while(true){val count=source.read(buffer,8192);if(count<0)break;total+=count;require(total<=4_000_000) {"共有サーバーの応答が大きすぎます"}}
                            val text=buffer.readUtf8()
                            if(!response.isSuccessful){val code=runCatching {json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content}.getOrNull().orEmpty().take(80);throw CloudHttpException(response.code,code)}
                            if(continuation.isActive)continuation.resume(text)
                        }catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}
                    }
                }
            })
        }
    }
    suspend fun register(endpoint:String,path:String,body:JsonObject)=json.decodeFromString<CloudRegistration>(request(endpoint,path,body=body))
    suspend fun checkVenue(endpoint:String,name:String,password:String):String = json.parseToJsonElement(request(endpoint,"venue/check",body=buildJsonObject {put("groupName",name);put("password",password)})).jsonObject["positionMode"]?.jsonPrimitive?.content?:"KYOTO"
    suspend fun setMemberPermission(session:CloudSession,memberId:String,canTriage:Boolean)=json.decodeFromString<CloudRosterEnvelope>(request(session.endpoint,"groups/${session.groupId}/member-permission",session.token,buildJsonObject {put("memberId",memberId);put("canTriage",canTriage)}))
    suspend fun setVenueClock(session:CloudSession,sessionId:String,minutes:Int)=json.decodeFromString<CloudRosterEnvelope>(request(session.endpoint,"groups/${session.groupId}/venue-clock",session.token,buildJsonObject {put("sessionId",sessionId);put("minutes",minutes)}))
    suspend fun startVenue(session:CloudSession,requestId:String,password:String,privateTraining:Boolean=false,title:String="",area:String="",positionMode:String="KYOTO",elapsedMinutes:Int=3,presentationMode:String="DRILL")=json.decodeFromString<CloudRosterEnvelope>(request(session.endpoint,"groups/${session.groupId}/venue",session.token,buildJsonObject {put("requestId",requestId);put("password",password);put("areaId","kyoto-gosho-south");put("positionMode",positionMode);put("elapsedMinutes",elapsedMinutes);put("presentationMode",presentationMode);if(privateTraining){put("privateTraining",true);put("title",title);put("area",area)}}))
    suspend fun renameGroup(session:CloudSession,name:String)=json.decodeFromString<CloudRosterEnvelope>(request(session.endpoint,"groups/${session.groupId}/name",session.token,buildJsonObject {put("groupName",name)}))
    suspend fun setMunicipality(session:CloudSession,code:String)=json.decodeFromString<CloudRosterEnvelope>(request(session.endpoint,"groups/${session.groupId}/municipality",session.token,buildJsonObject {put("code",code)}))
    suspend fun nearbyKey(session:CloudSession)=json.parseToJsonElement(request(session.endpoint,"groups/${session.groupId}/nearby",session.token)).jsonObject.getValue("key").jsonPrimitive.content.also {require(it.matches(Regex("[a-f0-9]{64}")))}
    suspend fun registerRelayKey(session:CloudSession,keyId:String,publicKeySpki:String) {
        request(session.endpoint,"groups/${session.groupId}/relay-keys",session.token,buildJsonObject {put("keyId",keyId);put("publicKeySpki",publicKeySpki)})
    }
    suspend fun relayKeys(session:CloudSession)=json.decodeFromString<io.github.nevaseme.plandmk1.relay.RelayPublicKeys>(request(session.endpoint,"groups/${session.groupId}/relay-keys",session.token))
    suspend fun invitation(session:CloudSession)=json.decodeFromString<CloudInvitation>(request(session.endpoint,"groups/${session.groupId}/invitations",session.token,buildJsonObject {}))
    suspend fun issueRecovery(session:CloudSession,memberId:String)=json.decodeFromString<CloudInvitation>(request(session.endpoint,"groups/${session.groupId}/access",session.token,buildJsonObject {put("action","issue");put("memberId",memberId)}))
    suspend fun revokeMember(session:CloudSession,memberId:String)=json.decodeFromString<CloudRosterEnvelope>(request(session.endpoint,"groups/${session.groupId}/access",session.token,buildJsonObject {put("action","revoke");put("memberId",memberId)}))
    override suspend fun roster(session:CloudSession)=json.decodeFromString<CloudRosterEnvelope>(request(session.endpoint,"groups/${session.groupId}/roster",session.token))
    override suspend fun pull(session:CloudSession,after:Long)=json.decodeFromString<CloudPage>(request(session.endpoint,"groups/${session.groupId}/records",session.token,after=after))
    override suspend fun push(session:CloudSession,records:List<CloudRecord>)=json.decodeFromString<CloudPushResult>(request(session.endpoint,"groups/${session.groupId}/records",session.token,json.encodeToJsonElement(CloudPush(records))))
}
