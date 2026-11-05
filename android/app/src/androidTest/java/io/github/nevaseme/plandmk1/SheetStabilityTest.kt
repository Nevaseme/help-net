package io.github.nevaseme.plandmk1

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.nevaseme.plandmk1.ui.PlanModalBottomSheet
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class SheetStabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tallScrollableSheetSettlesAfterUpwardFling() = checkSheet(false)

    @Test fun residentListSheetSettlesAfterFlingAndHeightChanges() = checkSheet(true)

    private fun checkSheet(lazyList: Boolean) {
        lateinit var state: SheetState
        var gap by mutableIntStateOf(80)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                PlanModalBottomSheet(
                    onDismissRequest = {}, sheetState = state,
                    dragHandle = { BottomSheetDefaults.DragHandle(Modifier.testTag("handle")) },
                ) {
                    if (lazyList) LazyColumn(Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp-gap).dp)) {
                        items(100) { Text("住民一覧 $it", Modifier.padding(16.dp)) }
                    } else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text("凡例・詳細の内容")
                        Spacer(Modifier.height((LocalConfiguration.current.screenHeightDp-gap).dp))
                    }
                }
            }
        }
        for (heightGap in listOf(80,100,120,140,160,200,0)) {
            compose.runOnUiThread { gap = heightGap }
            compose.mainClock.advanceTimeBy(2_000)
            compose.onNodeWithTag("handle", useUnmergedTree = true).performTouchInput {
                swipe(center, Offset(center.x, -350f), 80)
            }
            compose.mainClock.advanceTimeBy(2_000)
            val offsets = mutableListOf<Float>()
            repeat(90) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                compose.runOnUiThread { offsets += state.requireOffset() }
            }
            val movement = offsets.max() - offsets.min()
            android.util.Log.i("PlanD-sheet-test", "lazy=$lazyList gap=$heightGap range=$movement min=${offsets.min()} max=${offsets.max()}")
            assertTrue("Sheet must stop moving after release; gap=$heightGap range=$movement", movement < 1.1f)
        }
    }
}
