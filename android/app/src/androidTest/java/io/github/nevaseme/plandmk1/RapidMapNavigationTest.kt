package io.github.nevaseme.plandmk1

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.diagnostics.BuildingMapBenchmarkActivity
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.junit.Test
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

/** Switch away while Kyoto tiles are still loading, not after the map settles. */
class RapidMapNavigationTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val handler=Handler(Looper.getMainLooper())
    private fun main(block:()->Unit) {
        val done=CountDownLatch(1)
        handler.post {try {block()} finally {done.countDown()} }
        assertTrue("Main thread stalled during map navigation",done.await(3,TimeUnit.SECONDS))
    }
    private fun await(seconds:Long=15, condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+seconds*1000
        while(!condition()) {
            assertTrue("Navigation did not finish within ${seconds}s",SystemClock.elapsedRealtime()<end)
            SystemClock.sleep(20)
        }
    }
    private fun mapViews(view:View):List<MapView> = when(view) {
        is MapView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap {mapViews(view.getChildAt(it))}
        else -> emptyList()
    }
    @Test fun switchingTabsWhileKyotoLoadsStaysResponsive() {
        try { exerciseNavigation() } catch (failure: Throwable) {
            if(InstrumentationRegistry.getArguments().getString("observeFailure")=="true") {
                val started=SystemClock.elapsedRealtime()
                val responsive=CountDownLatch(1)
                handler.post { responsive.countDown() }
                val recovered=responsive.await(30,TimeUnit.SECONDS)
                android.util.Log.e("PlanD-rapid-tabs","Failed deadline: recovered=$recovered additionalWaitMs=${SystemClock.elapsedRealtime()-started}")
            }
            if(InstrumentationRegistry.getArguments().getString("holdFailure")=="true") {
                android.util.Log.e("PlanD-rapid-tabs","Holding failed process for native trace",failure)
                android.os.Process.sendSignal(android.os.Process.myPid(),3)
                SystemClock.sleep(30_000)
            }
            throw failure
        }
    }
    private fun exerciseNavigation() {
        val ref=AtomicReference<BuildingMapBenchmarkActivity>()
        if(InstrumentationRegistry.getArguments().getString("externalLaunch")!="true")
            inst.startActivitySync(Intent(inst.targetContext,BuildingMapBenchmarkActivity::class.java)
                .putExtra("navigationTest",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await(90) {main {ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<BuildingMapBenchmarkActivity>().firstOrNull()?.let(ref::set)};ref.get()!=null}
        val activity=ref.get()
        var retainedView: MapView? = null
        val frames=AtomicInteger()
        val startedFrames=AtomicInteger()
        val arguments=InstrumentationRegistry.getArguments()
        val traversal=arguments.getString("memoryTraversal")=="true"
        val delays=listOf(100L,350L,800L,1500L,2500L,350L)
        val route=listOf(LatLng(35.0,135.75),LatLng(35.03,135.75),LatLng(34.99,135.77),LatLng(35.01,135.72),LatLng(35.04,135.77))
        var cacheConfigured=false
        for(step in 0 until (if(traversal)10 else delays.size)) {
            val delayMs=delays[step%delays.size]
            main {activity.testTab=0}
            val mapRef=AtomicReference<MapLibreMap>();val viewRef=AtomicReference<MapView>()
            await {main {if(viewRef.get()==null) mapViews(activity.window.decorView).firstOrNull()?.let {
                viewRef.set(it);it.getMapAsync(mapRef::set)
            }};mapRef.get()!=null}
            val map=mapRef.get()
            if(retainedView==null) {
                retainedView=viewRef.get()
                main { viewRef.get().addOnDidFinishRenderingFrameListener { _, _ -> frames.incrementAndGet() } }
                main { viewRef.get().addOnWillStartRenderingFrameListener { startedFrames.incrementAndGet() } }
            }
            else assertTrue("Tab changes must reuse the map",retainedView===viewRef.get())
            await {var ready=false;main {ready=map.style?.isFullyLoaded==true};ready}
            if(!cacheConfigured) {
                if(arguments.getString("disableTileCache")=="true") main {
                    val started=SystemClock.elapsedRealtime()
                    map.setTileCacheEnabled(false)
                    android.util.Log.i("PlanD-rapid-tabs","disableTileCache setterMs=${SystemClock.elapsedRealtime()-started}")
                }
                cacheConfigured=true
            }
            main {map.animateCamera(CameraUpdateFactory.newLatLngZoom(if(traversal)route[step%route.size]else route.first(),11.5),300)}
            SystemClock.sleep(350)
            main {map.animateCamera(CameraUpdateFactory.zoomTo(15.5),300)}
            SystemClock.sleep(delayMs)
            val started=SystemClock.elapsedRealtime()
            main {activity.testTab=1}
            await(3) {activity.displayedTab==1}
            main {activity.testTab=2}
            await(3) {activity.displayedTab==2}
            main { assertTrue("Hidden map must stay alive without being displayed",
                !viewRef.get().isDestroyed && viewRef.get().visibility!=View.VISIBLE) }
            android.util.Log.i("PlanD-rapid-tabs","delay=$delayMs navigationMs=${SystemClock.elapsedRealtime()-started}")
        }
        // Already completed frame callbacks can still be queued on the UI thread.
        // Require a quiet 500 ms window, with a bounded 3 s settling deadline.
        var hiddenFrames=frames.get()
        var quietSince=SystemClock.elapsedRealtime()
        await(3) {
            val current=frames.get()
            if(current!=hiddenFrames) {hiddenFrames=current;quietSince=SystemClock.elapsedRealtime()}
            SystemClock.elapsedRealtime()-quietSince>=500
        }
        if(InstrumentationRegistry.getArguments().getString("inspectMemory")=="true") {
            fun memory(label:String):Long {
                val info=android.os.Debug.MemoryInfo()
                android.os.Debug.getMemoryInfo(info)
                val nativeBytes=android.os.Debug.getNativeHeapAllocatedSize()
                android.util.Log.i("PlanD-rapid-tabs","$label pssKb=${info.totalPss} nativeBytes=$nativeBytes")
                return nativeBytes
            }
            val framesWhileHidden=frames.get()
            val startedWhileHidden=startedFrames.get()
            SystemClock.sleep(10_000)
            val settledHiddenFrames=frames.get()
            android.util.Log.i("PlanD-rapid-tabs","lateHiddenFrameCallbacks=${settledHiddenFrames-framesWhileHidden} lateHiddenStartCallbacks=${startedFrames.get()-startedWhileHidden}")
            assertTrue("Hidden map must not start frames for cache maintenance",startedFrames.get()==startedWhileHidden)
            // Native completion notifications can arrive after the earlier
            // quiet window. Verify that the hidden view remains stopped after
            // the cache-maintenance delay, rather than counting late callbacks
            // as newly rendered frames.
            SystemClock.sleep(500)
            assertTrue("Hidden map must stay stopped after cache maintenance delay",frames.get()==settledHiddenFrames)
            memory("before-trim")
            val trim=InstrumentationRegistry.getArguments().getString("trimMemory")!="false"
            if(trim) main {
                // Exercise the system callback route, not a direct MapView call.
                (inst.targetContext.applicationContext as android.app.Application)
                    .onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            }
            SystemClock.sleep(10_000)
            memory(if(trim) "after-trim" else "control-no-trim")
            main {activity.testTab=0}
            await(3) {activity.displayedTab==0}
            SystemClock.sleep(10_000)
            val resumedNativeBytes=memory("after-resume")
            if (InstrumentationRegistry.getArguments().getString("verifyVisibleTrim")=="true") {
                main {
                    retainedView!!.getMapAsync { map ->
                        android.util.Log.i("PlanD-rapid-tabs","visibleTrim zoom=${map.cameraPosition.zoom}")
                        retainedView!!.onLowMemory()
                        map.triggerRepaint()
                    }
                }
                SystemClock.sleep(5_000)
                memory("after-direct-visible-trim")
            }
            main {activity.testTab=1}
            await(3) {activity.displayedTab==1}
            // Opt-in budget for repeated device comparisons, not a universal
            // assertion on allocator/driver behavior across Android devices.
            InstrumentationRegistry.getArguments().getString("maxNativeAfterResumeMb")?.toLong()?.let { budget ->
                assertTrue("Native heap after resume: $resumedNativeBytes bytes exceeds ${budget} MiB",
                    resumedNativeBytes <= budget*1024*1024)
            }
        }
        main {activity.finish()}
    }
}
