package io.github.nevaseme.plandmk1.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.nevaseme.plandmk1.PlanDApplication

class PositionResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        (context.applicationContext as PlanDApplication).position.restoreAfterBoot()
    }
}
