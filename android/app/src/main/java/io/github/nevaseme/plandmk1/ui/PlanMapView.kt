package io.github.nevaseme.plandmk1.ui

import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMapOptions

/** A Compose-owned MapView is not pooled/reused after removal from its window. */
class PlanMapView(context: Context) : MapView(
    context, MapLibreMapOptions.createFromAttributes(context).textureMode(true).localIdeographFontFamily("sans-serif")
) {
    private val appContext = context.applicationContext
    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() {
            if (!isDestroyed) this@PlanMapView.onLowMemory()
        }
        override fun onTrimMemory(level: Int) {
            // Includes UI_HIDDEN: the retained map can release unused tile cache
            // when the application leaves the foreground as well as under pressure.
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) onLowMemory()
        }
    }

    init { appContext.registerComponentCallbacks(memoryCallbacks) }

    override fun onDestroy() {
        if (isDestroyed) return
        appContext.unregisterComponentCallbacks(memoryCallbacks)
        super.onDestroy()
    }

    fun release() {
        if (isDestroyed) return
        onPause()
        onStop()
        onDestroy()
    }

    override fun onDetachedFromWindow() {
        // Compose can detach AndroidView before DisposableEffect.onDispose runs.
        // TextureView avoids SurfaceView's premature nativeReset while sources are
        // still loading. Release the owning map here, including its pending work.
        release()
        super.onDetachedFromWindow()
    }
}
