package io.github.nevaseme.plandmk1

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.eew.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Exercises Android storage/notification handling offline; this is not a claim of FCM delivery. */
class EewDeviceTest {
    @Test fun wolfxDiagnosticPersistsItsSourceAndDisplaysOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val directory = File(context.cacheDir, "wolfx-test-${UUID.randomUUID()}")
        val manager = context.getSystemService(NotificationManager::class.java)
        var tag: String? = null
        try {
            val now = System.currentTimeMillis()
            val date = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("Asia/Tokyo") }.format(now)
            val data = mapOf("schema" to "pland-eew-1", "source" to "wolfx", "region" to "京都府南部", "diagnostic" to "true",
                "raw" to """{"type":"jma_eew","EventID":"20261025120000","Serial":1,"AnnouncedTime":"$date","Issue":{"Status":"訓練"},"isTraining":true,"isWarn":true,"isCancel":false,"WarnArea":[{"Chiiki":"京都府南部","Type":"警報"}]}""")
            val store = EewReceiptStore(directory)
            val notice = requireNotNull(EewReceiver(store).receive(data, now, true))
            tag = notice.tag
            EewNotifications(context).show(notice)
            assertEquals("wolfx", EewReceiptStore(directory).receipts().single().warning.source)
            assertEquals(data["raw"], store.receipts().single().warning.raw)
            val displayed = manager.activeNotifications.first { it.id == EewNotifications.ID && it.tag == tag }
            assertEquals("PlanD 受信試験", displayed.notification.extras.getString("android.title"))
        } finally { tag?.let { manager.cancel(it, EewNotifications.ID) }; directory.deleteRecursively() }
    }
    @Test fun configuredFirebaseCanIssueADeviceToken() {
        assumeTrue("Firebase未設定のビルドでは実配信準備を確認しない", BuildConfig.EEW_FIREBASE_CONFIGURED)
        val token = Tasks.await(FirebaseMessaging.getInstance().token, 60, TimeUnit.SECONDS)
        assertTrue("FCMトークンを取得できること", token.length >= 20)
    }
    @Test fun backgroundReceiptPersistsAndShowsAClearlySeparatedDiagnostic() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val directory = File(context.cacheDir, "eew-test-${UUID.randomUUID()}")
        val manager = context.getSystemService(NotificationManager::class.java)
        var tag: String? = null
        try {
            val data = mapOf("schema" to "pland-eew-1", "source" to "p2pquake", "region" to "京都府南部", "diagnostic" to "true",
                "raw" to """{"id":"android-diagnostic","code":556,"test":true,"issue":{"eventId":"20261025120000","serial":"1","time":"2026/10/25 12:00:00"},"cancelled":false,"earthquake":{},"areas":[]}""")
            val notice = requireNotNull(EewReceiver(EewReceiptStore(directory)).receive(data, System.currentTimeMillis(), allowDiagnostics = true))
            tag = notice.tag
            EewNotifications(context).show(notice)
            assertEquals(1, EewReceiptStore(directory).receipts().size)
            val displayed = manager.activeNotifications.first { it.id == EewNotifications.ID && it.tag == notice.tag }
            assertEquals("PlanD 受信試験", displayed.notification.extras.getString("android.title"))
            assertTrue(displayed.notification.extras.getCharSequence("android.text").toString().contains("実際の地震ではありません"))
        } finally {
            tag?.let { manager.cancel(it, EewNotifications.ID) }
            directory.deleteRecursively()
        }
    }
}
