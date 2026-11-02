package io.github.nevaseme.plandmk1

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.relay.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real persistent worker uploads an absent author's signed record; no Activity or manual sync. */
class CloudRecoveryTest {
    private fun id()=UUID.randomUUID().toString()

    @Test fun savedForeignProofQueuesRealWorkerAndUploadsWithoutItsAuthor()=runBlocking<Unit> {
        val endpoint=InstrumentationRegistry.getArguments().getString("cloudEndpoint").orEmpty()
        assumeTrue("Explicit fictional server endpoint required",endpoint.isNotBlank())
        val app=ApplicationProvider.getApplicationContext<PlanDApplication>()
        val previousGroup=app.installation.group.value
        val name="relay-worker-${id()}";val alias="$name-author"
        val temporaryA=CloudSettings(app,"$name-a");val temporaryB=CloudSettings(app,"$name-b")
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        val api=CloudHttpApi();var sessionB:CloudSession?=null
        val manager=WorkManager.getInstance(app)
        try {
            val pair=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKeyPair()
            val authorSigner=object:RelaySigner {
                override val publicKeySpki=encode64(pair.public.encoded)
                override fun sign(body:ByteArray)=Signature.getInstance("SHA256withECDSA").run {initSign(pair.private);update(body);sign()}
            }
            val b=id();val a=id()
            val registrationB=temporaryB.submit(temporaryB.begin(endpoint,b,"架空中継端末B","署名中継のバックグラウンド試験"),api)
            val receiver=registrationB.session(endpoint);sessionB=receiver
            val invitation=api.invitation(receiver)
            val author=temporaryA.submit(temporaryA.begin(endpoint,a,"架空作成端末A",groupId=receiver.groupId,code=invitation.code),api).session(endpoint)
            api.registerRelayKey(author,authorSigner.keyId(),authorSigner.publicKeySpki)
            val membership=api.roster(receiver)
            CloudSettings(app).complete(receiver)
            RoomCloudJournal(app.database,receiver.groupId,b).updateRoster(membership,b)
            val profile=Resident(receiver.groupId,a,1,System.currentTimeMillis(),"架空・不在の作成者A",35.0,135.0,"1階","試験",Safety.SAFE)
            val record=CloudRecord("residents",Json {encodeDefaults=true}.encodeToJsonElement(profile).jsonObject)
            val envelope=RelayProofs.create(receiver.groupId,a,record,authorSigner)
            // A has left before B stores or uploads the receipt. Its signing key is unavailable.
            store.deleteEntry(alias)
            val repository=RoomRelayRepository(app.database,receiver.groupId,b,AndroidRelaySigner())
            assertTrue(repository.accept(envelope))
            assertEquals(a,RoomCloudJournal(app.database,receiver.groupId,b).records().single().owner())
            val work=checkNotNull(withContext(Dispatchers.IO) {CloudSyncWork.enqueue(app,receiver.groupId,afterChange=true)})
            val finished=withTimeout(60_000) {
                var state:WorkInfo
                while(true) {
                    state=checkNotNull(manager.getWorkInfoById(work).get(5,TimeUnit.SECONDS))
                    if(state.state.isFinished)break
                    delay(250)
                }
                state
            }
            assertEquals(WorkInfo.State.SUCCEEDED,finished.state)
            assertEquals(0,finished.outputData.getInt("rejected",-1))
            assertTrue(finished.outputData.getInt("uploaded",0)>=1)
            val stored=api.pull(receiver,0).records.single {it.collection=="residents"&&it.record["originId"]?.jsonPrimitive?.content==a}
            assertEquals("SAFE",stored.record["safety"]!!.jsonPrimitive.content)
            assertEquals("架空・不在の作成者A",stored.record["name"]!!.jsonPrimitive.content)
            assertEquals(previousGroup,app.installation.group.value)
        }finally {
            sessionB?.let {receiver->
                manager.cancelUniqueWork("cloud-sync:${receiver.groupId}").result.get(5,TimeUnit.SECONDS)
                app.database.withTransaction {
                    for(table in listOf("residents","member_rosters","relay_proofs"))
                        app.database.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE groupId = ?",arrayOf(receiver.groupId))
                }
                val prefs=app.getSharedPreferences("cloud-sharing",0);val edit=prefs.edit()
                prefs.all.keys.filter {it in setOf("session:${receiver.groupId}","nearby:${receiver.groupId}","relay-key:${receiver.groupId}")||it.startsWith("${receiver.endpoint}:${receiver.groupId}:")}.forEach {edit.remove(it)}
                check(edit.commit())
                app.getSharedPreferences("installation",0).edit().remove("incident:${receiver.groupId}").commit()
            }
            if(store.containsAlias(alias))store.deleteEntry(alias)
            listOf("$name-a","$name-b").forEach {app.getSharedPreferences(it,0).edit().clear().commit()}
        }
    }
}
