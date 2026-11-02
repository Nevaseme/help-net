package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity

/** Shared by every modal sheet, including short and dynamically growing details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    // Material3 1.4 derives top content padding from the moving sheet offset. Near full
    // height, this changes its measured height/anchor during overscroll and can bounce
    // forever. Reserve the top safe area OUTSIDE the draggable sheet, independently
    // of its offset. Keep bottom/side (including keyboard) protection inside as usual.
    val topPadding = with(LocalDensity.current) { WindowInsets.safeDrawing.getTop(this).toDp() }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.padding(top = topPadding),
        sheetState = sheetState,
        containerColor = containerColor,
        shape = shape,
        dragHandle = dragHandle,
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        },
        content = content,
    )
}
