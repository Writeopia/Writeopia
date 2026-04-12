package io.writeopia.drawing.viewmodel

import androidx.lifecycle.ViewModel
import io.writeopia.drawing.model.DrawingState
import io.writeopia.sdk.models.drawing.DrawingData
import io.writeopia.sdk.models.drawing.DrawingTool
import io.writeopia.sdk.models.drawing.Stroke
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

class DrawingViewModel : ViewModel() {

    private val _state = MutableStateFlow(DrawingState())
    val state: StateFlow<DrawingState> = _state.asStateFlow()

    private val strokeHistory = mutableListOf<List<Stroke>>()

    fun addStroke(stroke: Stroke) {
        // Save current state for undo
        strokeHistory.add(_state.value.drawingData.strokes)

        _state.update { currentState ->
            currentState.copy(
                drawingData = currentState.drawingData.copy(
                    strokes = currentState.drawingData.strokes + stroke
                ),
                canUndo = true
            )
        }
    }

    fun setTool(tool: DrawingTool) {
        _state.update { it.copy(currentTool = tool) }
    }

    fun setColor(color: Int) {
        _state.update { it.copy(currentColor = color) }
    }

    fun setStrokeWidth(width: Float) {
        _state.update { it.copy(strokeWidth = width) }
    }

    fun undo() {
        if (strokeHistory.isNotEmpty()) {
            val previousStrokes = strokeHistory.removeLast()
            _state.update { currentState ->
                currentState.copy(
                    drawingData = currentState.drawingData.copy(strokes = previousStrokes),
                    canUndo = strokeHistory.isNotEmpty()
                )
            }
        }
    }

    fun clear() {
        // Save current state for undo
        strokeHistory.add(_state.value.drawingData.strokes)

        _state.update { currentState ->
            currentState.copy(
                drawingData = currentState.drawingData.copy(strokes = emptyList()),
                canUndo = true
            )
        }
    }

    fun setCanvasSize(width: Int, height: Int) {
        _state.update { currentState ->
            currentState.copy(
                drawingData = currentState.drawingData.copy(
                    width = width,
                    height = height
                )
            )
        }
    }

    fun getDrawingJson(): String {
        return Json.encodeToString(DrawingData.serializer(), _state.value.drawingData)
    }

    fun loadDrawing(json: String) {
        try {
            val drawingData = Json.decodeFromString(DrawingData.serializer(), json)
            _state.update { currentState ->
                currentState.copy(
                    drawingData = drawingData,
                    canUndo = false
                )
            }
            strokeHistory.clear()
        } catch (e: Exception) {
            // If parsing fails, start with empty drawing
        }
    }

    fun loadDrawingData(drawingData: DrawingData) {
        _state.update { currentState ->
            currentState.copy(
                drawingData = drawingData,
                canUndo = false
            )
        }
        strokeHistory.clear()
    }

    companion object {
        val PRESET_COLORS = listOf(
            0xFF000000.toInt(), // Black
            0xFFFFFFFF.toInt(), // White
            0xFFFF0000.toInt(), // Red
            0xFF00FF00.toInt(), // Green
            0xFF0000FF.toInt(), // Blue
            0xFFFFFF00.toInt(), // Yellow
            0xFFFF00FF.toInt(), // Magenta
            0xFF00FFFF.toInt(), // Cyan
            0xFFFF8000.toInt(), // Orange
            0xFF8000FF.toInt(), // Purple
            0xFF808080.toInt()  // Gray
        )

        val STROKE_WIDTHS = listOf(4f, 8f, 12f, 16f, 20f)
    }
}
