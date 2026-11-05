package io.github.nevaseme.plandmk1.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.Layout

internal val LocalMapVisible = compositionLocalOf { true }

/** Keep one map per screen owner; tab changes must not synchronously drain tile workers. */
@Composable
fun RetainedMapContent(visible: Boolean, content: @Composable () -> Unit) {
    var visited by remember { mutableStateOf(false) }
    SideEffect { if (visible) visited = true }
    if (visible || visited) {
        CompositionLocalProvider(LocalMapVisible provides visible) {
            Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
                val children = measurables.map { it.measure(constraints) }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // Unplaced content has no drawing, touch targets or semantics.
                    if (visible) children.forEach { it.place(0, 0) }
                }
            }
        }
    }
}
