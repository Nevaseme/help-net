package io.github.nevaseme.plandmk1.cloud

import io.github.nevaseme.plandmk1.PlanDApplication
import io.github.nevaseme.plandmk1.core.MemberRole
import io.github.nevaseme.plandmk1.core.Safety
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

data class CloudState(val groupId:String="",val groupName:String="",val connected:Boolean=false,val admin:Boolean=false,val busy:Boolean=false,val status:String="未接続",val error:String?=null,val invitation:String?=null,val unsentForeign:Int=0,val rejected:Int=0,val pendingRegistration:Boolean=false,val nearbyReady:Boolean=false,val relayReady:Boolean=false,val venue:io.github.nevaseme.plandmk1.demo.VenueSession?=null,val municipality:GroupMunicipality?=null,val syncError:String?=null)

/** Production HTTPS sharing. Requests are serialized; receipts stay durable across cancellation. */
class CloudController(private val app:PlanDApplication) {
    private val settings=CloudSettings(app)
    private val api=CloudHttpApi()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val lock=Mutex()
    private val mutable=MutableStateFlow(CloudState())
    val state=mutable.asStateFlow()
    // Transport activity belongs only to communication details. It must not invalidate
    // forms, map screens, or training controls every time the polling timer fires.
    private val syncActivity=MutableStateFlow(false)
    val synchronization=syncActivity.asStateFlow()
    // Transient form state survives LazyColumn disposal / keyboard resize; never persisted.
    val venuePasswordDraft=MutableStateFlow("")
    val venueAdminPasswordDraft=MutableStateFlow("")
    val venuePositionModeDraft=MutableStateFlow("KYOTO")
    val venueNameStep=MutableStateFlow(false)
    val venueEntryPositionMode=MutableStateFlow("KYOTO")
    val venueChanges=MutableStateFlow(0L)
    val recoveryCode=MutableStateFlow<String?>(null)
    fun endVenue() {
        val session=settings.session(group)?:return;val venue=settings.venue(group)?:return
        if(session.role!=MemberRole.ADMIN||venue.closed||!beginOperation("デモを終了しています…"))return
        scope.launch {lock.withLock {
            try {withContext(Dispatchers.IO){app.quakes.command(io.github.nevaseme.plandmk1.core.QuakeEvent(group,java.util.UUID.randomUUID().toString(),session.deviceId,System.currentTimeMillis(),io.github.nevaseme.plandmk1.core.QuakeAction.END,true,targetId=venue.incidentId))};app.recordsChanged()}
            catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=mutable.value.copy(error=safeError(e))}
            finally {mutable.value=mutable.value.copy(busy=false)}
        };sync()}
    }
    fun recover(endpoint:String,groupName:String,code:String)=register(endpoint,"",venueName=groupName,recovery=code)
    fun manageAccess(memberId:String?,revoke:Boolean=false) {
        val session=settings.session(group)?:return
        if(!beginOperation(if(revoke)"参加登録を停止しています…" else "復元コードを発行しています…"))return
        scope.launch {lock.withLock {
            try {withContext(Dispatchers.IO){if(revoke){val body=api.revokeMember(session,memberId?:session.memberId);rememberVenue(body);recoveryCode.value=null}else recoveryCode.value=api.issueRecovery(session,memberId?:session.memberId).code}}
            catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=mutable.value.copy(error=safeError(e))}
            finally{mutable.value=mutable.value.copy(busy=false)}
        }}
    }
    fun venue(target:String)=settings.venue(target)
    fun hasSession(target:String)=settings.session(target)!=null
    fun isVenue(target:String)=settings.venue(target)?.let {it.positionMode=="KYOTO"&&it.active(target,app.installation.incidentSelection(target))}==true
    private fun rememberVenue(body:CloudRosterEnvelope) {
        if(body.venue?.closed==false&&settings.venue(body.groupId)?.sessionId!=body.venue.sessionId)settings.setVenueOnboarding(body.groupId,body.venue.sessionId)
        if(settings.venue(body.groupId)!=body.venue||settings.municipality(body.groupId)!=body.municipality){settings.saveVenue(body);venueChanges.value++}
        if(group==body.groupId)mutable.value=mutable.value.copy(venue=body.venue,municipality=body.municipality)
    }
    private var visible=false
    private var polling:Job?=null
    private var syncing:Job?=null
    private var registering:Job?=null
    private var again=false
    private var dirty=false
    private val queuedGroups=mutableSetOf<String>()
    private var group=app.installation.group.value
    init {scope.launch {app.installation.group.collect {value->
        syncing?.cancel();group=value;dirty=false;refresh();restartPolling()
    }}}
    private fun refresh() {
        val session=settings.session(group)
        mutable.value=CloudState(group,session?.groupName.orEmpty(),session!=null,session?.role==MemberRole.ADMIN,status=if(session==null)"未接続" else "接続済み",pendingRegistration=settings.pending()!=null,nearbyReady=settings.nearbyKey(group)!=null,relayReady=settings.relayKeyRegistered(group),venue=settings.venue(group),municipality=settings.municipality(group))
    }
    fun setVisible(value:Boolean) {
        if(!value&&dirty)queueRetry(group,afterChange=true)
        if(value)queuedGroups.remove(group)
        visible=value;restartPolling()
    }
    private fun restartPolling() {
        polling?.cancel();polling=null
        if(visible)polling=scope.launch {while(isActive){sync();delay(15_000)}}
    }
    fun changed() {
        dirty=true
        queueRetry(group,afterChange=true)
        if(visible)sync()
    }
    private fun queueRetry(target:String,afterChange:Boolean=false) {
        // New durable data must outlive this controller's previous in-memory retry marker.
        // WorkManager coalesces change notifications with its current persisted queue state.
        if(!afterChange&&!queuedGroups.add(target))return
        scope.launch {
            try {withContext(Dispatchers.IO){CloudSyncWork.enqueue(app,target,afterChange=afterChange)}}
            catch(error:CancellationException){throw error}
            catch(_:Exception){queuedGroups.remove(target)}
        }
    }
    private suspend fun performSync(session:CloudSession)=withContext(Dispatchers.IO) {
        // Credential provisioning may be unavailable on an older server; ordinary sync still works.
        if(settings.nearbyKey(session.groupId)==null)try {
            settings.saveNearbyKey(session.groupId,api.nearbyKey(session))
        }catch(e:CancellationException){throw e}catch(_:Exception){ }
        if(!settings.relayKeyRegistered(session.groupId)) {
            val signer=io.github.nevaseme.plandmk1.relay.AndroidRelaySigner()
            api.registerRelayKey(session,signer.keyId(),signer.publicKeySpki)
            settings.markRelayKeyRegistered(session.groupId)
        }
        val selection=settings.selection(session,{app.installation.incidentSelection(session.groupId)},{app.installation.selectIncident(session.groupId,it)})
        val result=CloudSyncEngine(session,api,RoomCloudJournal(app.database,session.groupId,session.deviceId,selection,onRoster=::rememberVenue){app.position.state.value.active&&app.installation.group.value==session.groupId&&!isVenue(session.groupId)},settings.checkpoint(session)).sync()
        val demo=settings.venue(session.groupId)
        if(demo!=null&&!demo.closed&&settings.venueOnboarding(session.groupId)==demo.sessionId&&app.quakes.states(session.groupId).any {demo.incidentId in it.incidentIds}) {
            app.installation.selectIncident(session.groupId,demo.incidentId);settings.setVenueOnboarding(session.groupId,null)
        }
        result
    }
    /** One background job uses the queued group, even if the user has since selected another. */
    suspend fun syncStoredGroup(target:String):CloudSyncResult?=lock.withLock {
        val session=settings.session(target)?:return@withLock null
        performSync(session)
    }
    fun sync() {
        val session=settings.session(group)?:return
        if(syncing?.isActive==true){again=true;return}
        syncing=scope.launch {
            do {
                again=false
                lock.withLock {
                    if(group!=session.groupId)return@withLock
                    syncActivity.value=true
                    try {
                        val result=performSync(session)
                        if(group==session.groupId){dirty=false;queuedGroups.remove(group)}
                        if(group==session.groupId)mutable.value=mutable.value.copy(status=if(result.unsentForeign+result.rejected==0)"同期済み" else "未送信の情報があります",syncError=null,unsentForeign=result.unsentForeign,rejected=result.rejected)
                    }catch(e:CancellationException){throw e}
                    catch(e:Exception){
                        if(retryCloudFailure(e))queueRetry(session.groupId)
                        if(group==session.groupId)mutable.value=mutable.value.copy(syncError=safeError(e),status="未送信の情報を端末に保存しています")
                    }
                    finally {syncActivity.value=false;if(group==session.groupId)mutable.value=mutable.value.copy(nearbyReady=settings.nearbyKey(group)!=null,relayReady=settings.relayKeyRegistered(group))}
                }
            }while(again&&group==session.groupId)
        }
    }
    fun create(endpoint:String,name:String,groupName:String)=register(endpoint,name,groupName=groupName)
    fun retryRegistration() {
        val pending=settings.pending()?:return
        if(pending.path=="venue/join"){mutable.value=mutable.value.copy(error="会場デモのパスワードを入力し、参加を再試行してください");return}
        fun field(key:String)=pending.body[key]?.jsonPrimitive?.content
        if(pending.path=="venue/recover"){recover(pending.endpoint,field("groupName").orEmpty(),field("code").orEmpty());return}
        if(pending.path=="groups")register(pending.endpoint,field("activityName").orEmpty(),groupName=field("groupName"))
        else register(pending.endpoint,field("activityName").orEmpty(),groupId=pending.path.split('/')[1],code=field("code"))
    }
    fun join(endpoint:String,name:String,invitation:String) {
        val parts=invitation.trim().split(':')
        if(parts.size!=2){mutable.value=mutable.value.copy(error="招待コードをグループIDからまとめて貼り付けてください");return}
        register(endpoint,name,groupId=parts[0],code=parts[1])
    }
    private fun register(endpoint:String,name:String,groupName:String?=null,groupId:String?=null,code:String?=null,venueName:String?=null,venuePassword:String?=null,recovery:String?=null) {
        if(registering?.isActive==true||!beginOperation("グループに接続しています…"))return
        val previousGroup=app.installation.group.value
        registering=scope.launch {lock.withLock {
            try {
                val session=withContext(Dispatchers.IO) {
                    val pending=if(recovery!=null)settings.beginRecovery(endpoint.trim(),app.installation.localId,venueName.orEmpty().trim(),recovery.trim()) else if(venueName!=null)settings.beginVenue(endpoint.trim(),app.installation.localId,name.trim(),venueName.trim()) else settings.begin(endpoint.trim(),app.installation.localId,name.trim(),groupName?.trim(),groupId,code)
                    val submitted=if(venueName!=null&&recovery==null)pending.copy(body=JsonObject(pending.body+ ("password" to JsonPrimitive(venuePassword.orEmpty())))) else pending
                    val result=settings.submit(submitted,api)
                    require(result.deviceId==app.installation.localId&&result.token==pending.body.getValue("registrationToken").jsonPrimitive.content)
                    if(venueName==null)require(result.groupId==(groupId?:pending.body.getValue("requestId").jsonPrimitive.content))
                    val member=result.roster.members.single {result.deviceId in it.deviceIds}
                    require(member.memberId==result.memberId&&member.role==result.role)
                    RoomCloudJournal(app.database,result.groupId,result.deviceId).updateRoster(result.envelope(),result.deviceId)
                    rememberVenue(result.envelope())
                    if(venueName!=null)settings.setVenueOnboarding(result.groupId,checkNotNull(result.venue).sessionId)
                    if(venueName==null&&app.store.get(result.groupId,result.deviceId)==null) {
                        app.store.get(previousGroup,result.deviceId)?.let {profile->
                            app.store.updateOwn(result.groupId,result.deviceId) {existing->existing?:profile.copy(groupId=result.groupId,name=name.trim(),safety=Safety.UNREPORTED)}
                        }
                    }
                    result.session(pending.endpoint).also {settings.complete(it)}
                }
                app.position.stop();app.sharing.stop();app.lab.stop(text="グループを変更したため停止しました")
                app.installation.setGroup(session.groupId)
                settings.cancelRejectedRegistration()
                group=session.groupId;venuePasswordDraft.value="";venueNameStep.value=false;recoveryCode.value=null;refresh()
            }catch(e:CancellationException){throw e}
            catch(e:Exception){mutable.value=mutable.value.copy(error=safeError(e),status="接続を完了できませんでした")}
            finally {mutable.value=mutable.value.copy(busy=false,pendingRegistration=settings.pending()!=null)}
        };sync()}
    }
    fun checkVenue(endpoint:String,name:String,password:String,onSuccess:()->Unit) {
        if(!beginOperation("参加情報を確認しています…"))return
        scope.launch {lock.withLock {
            try {venueEntryPositionMode.value=withContext(Dispatchers.IO){api.checkVenue(endpoint,name.trim(),password)};onSuccess()}
            catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=mutable.value.copy(error=safeError(e))}
            finally {mutable.value=mutable.value.copy(busy=false)}
        }}
    }
    fun joinVenue(endpoint:String,venueName:String,password:String,name:String)=register(endpoint,name,venueName=venueName,venuePassword=password)
    fun startVenue(password:String,privateTraining:Boolean=false,title:String="",area:String="",positionMode:String="KYOTO",elapsedMinutes:Int=if(privateTraining)0 else 3,onStarted:()->Unit={}) {
        val session=settings.session(group)?:run {mutable.value=mutable.value.copy(error="架空の位置を共有するにはグループへの接続が必要です");return}
        if(session.role!=MemberRole.ADMIN||!beginOperation("訓練を開始しています…"))return
        scope.launch {var started=false;lock.withLock {
            try {val body=withContext(Dispatchers.IO){api.startVenue(session,settings.venueStart(session.groupId),password,privateTraining,title,area,positionMode,elapsedMinutes)}
                rememberVenue(body);settings.setVenueOnboarding(session.groupId,checkNotNull(body.venue).sessionId);settings.finishVenueStart(session.groupId);venueAdminPasswordDraft.value="";started=true
            }catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=mutable.value.copy(error=safeError(e))}
            finally {mutable.value=mutable.value.copy(busy=false)}
        };if(started){sync();onStarted()}}
    }
    fun setVenueClock(minutes:Int) {
        val session=settings.session(group)?:return
        val venue=mutable.value.venue?:return
        if(session.role!=MemberRole.ADMIN||!beginOperation("デモの経過時間を変更しています…"))return
        scope.launch {lock.withLock {
            try {val body=withContext(Dispatchers.IO){api.setVenueClock(session,venue.sessionId,minutes)};rememberVenue(body)}
            catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=mutable.value.copy(error=safeError(e))}
            finally {mutable.value=mutable.value.copy(busy=false)}
        }}
    }
    fun setMunicipality(code:String) {
        val session=settings.session(group)?:return
        if(session.role!=MemberRole.ADMIN||!beginOperation("活動地域を保存しています…"))return
        scope.launch {lock.withLock {
            if(group!=session.groupId)return@withLock
            try {val body=withContext(Dispatchers.IO){api.setMunicipality(session,code)};rememberVenue(body)}
            catch(e:CancellationException){throw e}
            catch(e:Exception){if(group==session.groupId)mutable.value=mutable.value.copy(error=safeError(e))}
            finally {if(group==session.groupId)mutable.value=mutable.value.copy(busy=false)}
        }}
    }
    fun invite() {
        val session=settings.session(group)?:return
        if(session.role!=MemberRole.ADMIN||!beginOperation("招待コードを作成しています…"))return
        scope.launch {lock.withLock {
            if(group!=session.groupId)return@withLock
            try {val invite=withContext(Dispatchers.IO){api.invitation(session)}
                if(group==session.groupId)mutable.value=mutable.value.copy(invitation="${session.groupId}:${invite.code}")
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(group==session.groupId)mutable.value=mutable.value.copy(error=safeError(e))}
            finally {if(group==session.groupId)mutable.value=mutable.value.copy(busy=false)}
        }}
    }
    // Accept a tap while sync holds the mutex; reserve the operation immediately so
    // repeated taps cannot enqueue duplicate writes while waiting for that sync.
    private fun beginOperation(status:String):Boolean {
        if(mutable.value.busy)return false
        mutable.value=mutable.value.copy(busy=true,error=null,status=status)
        return true
    }
    private fun safeError(error:Exception)=when(error) {
        is CloudHttpException->when(error.code){
            "invalid_venue_credentials"->"グループ名またはパスワードを確認してください"
            "weak_venue_password", "invalid_venue_password"->"参加パスワードを1〜128文字で入力してください"
            "invalid_recovery"->"復元コードを確認してください。発行から24時間以内に1回使えます"
            "access_revoked"->"この参加登録は停止されています。管理者に確認してください"
            "session_expired"->"接続の有効期限が切れました。復元コードを使うか、管理者に確認してください"
            "member_recovery_only"->"管理者の認証はこの操作では変更できません"
            "venue_closed"->"この会場デモは終了しています"
            "venue_name_exists"->"同じ名前の会場デモがあります。別のグループ名を使用してください"
            "rate_limited"->"入力が集中しています。1分ほど待って再試行してください"
            else->error.message?:"共有サーバーに接続できません"
        }
        else->"共有を完了できませんでした。入力と通信を確認して再試行してください"
    }
}
