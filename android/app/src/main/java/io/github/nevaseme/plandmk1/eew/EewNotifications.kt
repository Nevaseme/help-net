package io.github.nevaseme.plandmk1.eew

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.nevaseme.plandmk1.MainActivity

class EewNotifications(private val context: Context) {
    fun show(notice: EewNotice) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "緊急地震速報", NotificationManager.IMPORTANCE_HIGH))
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("pland://eew/${notice.eventId}"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(notice.title).setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .build()
        manager.notify(notice.tag, ID, notification)
    }
    companion object { const val CHANNEL = "eew"; const val ID = 556 }
}
