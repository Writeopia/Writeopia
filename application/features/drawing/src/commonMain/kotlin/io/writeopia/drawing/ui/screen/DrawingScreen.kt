package io.writeopia.drawing.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.writeopia.drawing.ui.components.DrawingCanvas
import io.writeopia.drawing.ui.components.DrawingToolbar
import io.writeopia.drawing.viewmodel.DrawingViewModel
import io.writeopia.sdk.models.drawing.DrawingData

@Composable
fun DrawingScreen(
    viewModel: DrawingViewModel,
    onSave: (DrawingData) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialDrawingJson: String? = null
) {
    val state by viewModel.state.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()

    // Set initial color based on theme (white for dark mode, black for light mode)
    LaunchedEffect(Unit) {
        val initialColor = if (isDarkTheme) {
            0xFFFFFFFF.toInt() // White
        } else {
            0xFF000000.toInt() // Black
        }
        viewModel.setColor(initialColor)
    }

    LaunchedEffect(initialDrawingJson) {
        initialDrawingJson?.let { json ->
            if (json.isNotEmpty()) {
                viewModel.loadDrawing(json)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DrawingTopBar(
                onSave = { onSave(state.drawingData) },
                onCancel = onCancel
            )
        },
        bottomBar = {
            DrawingToolbar(
                currentTool = state.currentTool,
                currentColor = state.currentColor,
                strokeWidth = state.strokeWidth,
                canUndo = state.canUndo,
                colors = DrawingViewModel.PRESET_COLORS,
                strokeWidths = DrawingViewModel.STROKE_WIDTHS,
                onToolSelected = viewModel::setTool,
                onColorSelected = viewModel::setColor,
                onStrokeWidthSelected = viewModel::setStrokeWidth,
                onUndo = viewModel::undo,
                onClear = viewModel::clear
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(vertical = 4.dp)
        ) {
            DrawingCanvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .onSizeChanged { size ->
                        viewModel.setCanvasSize(size.width, size.height)
                    },
                strokes = state.drawingData.strokes,
                currentTool = state.currentTool,
                currentColor = state.currentColor,
                strokeWidth = state.strokeWidth,
                onStrokeAdded = viewModel::addStroke
            )
        }
    }
}

@Composable
private fun DrawingTopBar(
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onCancel) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Drawing",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onSave) {
            Text("Save")
        }
    }
}
