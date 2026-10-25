package io.github.nevaseme.plandmk1.eew

import android.content.Context
import androidx.work.*
import com.google.firebase.messaging.FirebaseMessaging
import io.github.nevaseme.plandmk1.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object EewRegistration {
    fun schedule(context: Context) {
        if (!BuildConfig.EEW_FIREBASE_CONFIGURED || !BuildConfig.EEW_RELAY_URL.startsWith("https://")) {
            android.util.Log.i("PlanD-EEW", "Firebaseまたは中継先が未設定のため速報受信登録は未完了です")
            return
        }
        val request = OneTimeWorkRequestBuilder<EewRegistrationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("eew-registration", ExistingWorkPolicy.REPLACE, request)
    }
}

/** WorkManager is for deferred registration only; actual warnings never wait for a scheduled job. */
class EewRegistrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.EEW_FIREBASE_CONFIGURED || !BuildConfig.EEW_RELAY_URL.startsWith("https://")) return Result.failure()
        return try {
            val token = suspendCancellableCoroutine<String> { continuation ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
            withContext(Dispatchers.IO) {
                val prefs = applicationContext.getSharedPreferences("eew-registration", Context.MODE_PRIVATE)
                val id = prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
                    check(prefs.edit().putString("id", it).commit())
                }
                val body = buildJsonObject { put("id", id); put("token", token) }.toString()
                val request = Request.Builder().url(BuildConfig.EEW_RELAY_URL.trimEnd('/') + "/register")
                    .post(body.toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> { prefs.edit().putLong("registeredAt", System.currentTimeMillis()).apply(); Result.success() }
                        response.code == 429 || response.code >= 500 -> Result.retry()
                        else -> { android.util.Log.w("PlanD-EEW", "受信登録を拒否されました: ${response.code}"); Result.failure() }
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
          catch (_: Exception) { Result.retry() }
    }
    companion object { private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }
}
