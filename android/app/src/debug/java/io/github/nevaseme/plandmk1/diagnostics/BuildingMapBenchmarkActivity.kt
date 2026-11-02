package io.github.nevaseme.plandmk1.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.ui.SituationScreen
import io.github.nevaseme.plandmk1.ui.RetainedMapContent
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.maplibre.android.MapLibre

/** Uses the production map without reading or changing resident/incident records. */
class BuildingMapBenchmarkActivity:ComponentActivity() {
    val createdAt=android.os.SystemClock.elapsedRealtime()
    var testTab by mutableIntStateOf(0)
    @Volatile var displayedTab = -1
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        MapLibre.getInstance(this)
        val navigationTest=intent.getBooleanExtra("navigationTest",false)
        setContent {PlanDMk1Theme {
            Column {
                Box(Modifier.weight(1f)) {
                    RetainedMapContent(testTab==0) { SituationScreen(emptyList(),emptyList(),
                        if(navigationTest) MapPoint(35.72,139.76) else MapPoint(35.003,135.759),System.currentTimeMillis())
                    }
                    if(testTab!=0) Text(if(testTab==1) "住民一覧" else "自分の安否")
                }
                if(navigationTest) Row {
                    listOf("地図","一覧","安否").forEachIndexed { i,label -> TextButton(onClick={testTab=i}) {Text(label)} }
                }
            }
            SideEffect {displayedTab=testTab}
        }}
    }
}
