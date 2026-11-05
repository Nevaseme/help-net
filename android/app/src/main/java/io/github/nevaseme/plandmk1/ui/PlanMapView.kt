package io.github.nevaseme.plandmk1.ui

import android.content.Context
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMapOptions

/** A Compose-owned MapView is not pooled/reused after removal from its window. */
class PlanMapView(context: Context) : MapView(
    context, MapLibreMapOptions.createFromAttributes(context).textureMode(true).localIdeographFontFamily("sans-serif")
) {
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
