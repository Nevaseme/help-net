package io.github.nevaseme.plandmk1

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraUpdateFactory
import java.io.File

/** Synthetic map only: no changes to the current group, resident records or server. */
class MapLayerDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun reportsSharePeopleLayerAndCardsStayAboveWithWorkingTaps() {
        val inst=InstrumentationRegistry.getInstrumentation()
        inst.runOnMainSync {org.maplibre.android.MapLibre.getInstance(inst.targetContext)}
        val anchor=MapPoint(35.0145,135.7625)
        val covered=MapPoint(35.0146,135.7625)
        val exposed=MapPoint(35.0145,135.7635)
        val now=System.currentTimeMillis()
        val reports=listOf(MapReport("covered",covered,Urgency.HIGH),MapReport("exposed",exposed,Urgency.UNSET))
        var selected=""
        shellComposeContent {CompositionLocalProvider(LocalUiLanguage provides "en") {PlanDMk1Theme {
            SituationMap(listOf(MapPerson("person","SOS",MapSafety.SOS,point=covered,acquiredAt=now)),emptyList(),anchor,true,now,false,SituationCamera(),
                {selected="person"},{},{},{},Modifier.fillMaxSize(),reports=reports,onReport={selected=it},initialZoom=18.0,
                neighborhoods=listOf(MapNeighborhood(CommunityRow("area","Layer check","Layer check",sos=1),anchor,1)),onNeighborhood={selected=it})
        } } }
        var view:MapView?=null;var map:MapLibreMap?=null
        fun find(v:View):MapView?=if(v is MapView)v else if(v is ViewGroup)(0 until v.childCount).firstNotNullOfOrNull {find(v.getChildAt(it))} else null
        compose.waitUntil(20000) {compose.runOnIdle {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()?.let {activity->
                find(activity.window.decorView)?.let {view=it;it.getMapAsync {m->map=m}}
            }
        };map!=null}
        compose.waitUntil(20000){var count=0;compose.runOnIdle{count=map!!.markers.size};count==3}
        compose.onNodeWithContentDescription("Layer check").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Incident report").assertCountEquals(2)
        android.os.SystemClock.sleep(1500)
        val screenshot=inst.uiAutomation.takeScreenshot()
        val file=File(inst.targetContext.getExternalFilesDir(null),"map-layer-check.png")
        file.outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.runOnIdle {
            assertTrue(map!!.markers.any {it.position==LatLng(covered.latitude,covered.longitude)&&it.title=="Incident report"})
            val xy=IntArray(2);view!!.getLocationOnScreen(xy)
            val p=map!!.projection.toScreenLocation(LatLng(covered.latitude,covered.longitude))
            var white=0
            for(y in -8..8)for(x in -8..8) {
                val pixel=screenshot.getPixel(xy[0]+p.x.toInt()+x,xy[1]+p.y.toInt()+y)
                if(android.graphics.Color.red(pixel)>240&&android.graphics.Color.green(pixel)>240&&android.graphics.Color.blue(pixel)>240)white++
            }
            assertTrue("Card must visibly cover the report and SOS: white=$white/289",white>220)
            val flag=map!!.projection.toScreenLocation(LatLng(exposed.latitude,exposed.longitude))
            val radius=(24*inst.targetContext.resources.displayMetrics.density).toInt()
            var flagPixels=0
            for(y in -radius..radius)for(x in -radius..radius) {
                val px=xy[0]+flag.x.toInt()+x;val py=xy[1]+flag.y.toInt()+y
                if(px in 0 until screenshot.width&&py in 0 until screenshot.height&&screenshot.getPixel(px,py) and 0xFFFFFF==0x68778D)flagPixels++
            }
            assertTrue("Uncovered native report must be visibly rendered: pixels=$flagPixels",flagPixels>100)
        }
        screenshot.recycle()
        compose.onNodeWithContentDescription("Layer check").performTouchInput {click(center)}
        compose.runOnIdle {assertEquals("area",selected)}
        compose.onAllNodesWithContentDescription("Incident report").onLast().performTouchInput {click(center)}
        compose.runOnIdle {assertEquals("exposed",selected)}
        repeat(8) {i->
            compose.runOnIdle {map!!.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(anchor.latitude,anchor.longitude+(if(i%2==0).0004 else 0.0)),if(i%2==0)17.5 else 18.0),250)}
            android.os.SystemClock.sleep(350)
            compose.onNodeWithContentDescription("Layer check").assertIsDisplayed()
        }
        compose.runOnIdle {ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach {it.finish()}}
    }
}
