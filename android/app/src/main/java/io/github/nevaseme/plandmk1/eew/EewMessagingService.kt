package io.github.nevaseme.plandmk1.eew

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.nevaseme.plandmk1.BuildConfig
import io.github.nevaseme.plandmk1.PlanDApplication

class EewMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { EewRegistration.schedule(this) }

    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as PlanDApplication
        try {
            val notice = app.eewReceiver.receive(message.data, System.currentTimeMillis(), allowDiagnostics = BuildConfig.DEBUG)
            if (notice != null) EewNotifications(this).show(notice)
        } catch (e: Exception) {
            // Do not log a device token, the payload, or user data.
            android.util.Log.e("PlanD-EEW", "速報の保存または通知に失敗しました", e)
        }
    }
}
