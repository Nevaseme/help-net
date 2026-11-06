package io.github.nevaseme.plandmk1

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import org.junit.Test
import java.io.File

/** App-owned exit traces only; never reads resident records or exports them to the server. */
@SdkSuppress(minSdkVersion = 30)
class AppExitDiagnosticsTest {
    @Test fun exportLastCrashTrace() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val exit=context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName,0,30)
            .firstOrNull {it.reason==ApplicationExitInfo.REASON_CRASH&&it.processName==context.packageName}
        val file=File(context.getExternalFilesDir(null),"helpnet-last-crash.txt")
        file.writeText("timestamp=${exit?.timestamp} reason=${exit?.reason} description=${exit?.description}\n")
        exit?.traceInputStream?.use {input->file.appendText(input.bufferedReader().readText())}
    }
}
