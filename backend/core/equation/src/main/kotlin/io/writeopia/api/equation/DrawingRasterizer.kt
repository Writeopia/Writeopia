package io.writeopia.api.equation

import io.writeopia.sdk.models.drawing.DrawingData
import io.writeopia.sdk.models.drawing.DrawingTool
import io.writeopia.sdk.models.drawing.Stroke
import kotlinx.serialization.json.Json
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Draws the strokes of a drawing into a PNG for the model to read: black ink on white, cropped to
 * the strokes with a margin, scaled so the longest side is [MAX_SIDE] pixels at most. The colors
 * of the strokes are ignored, so a white drawing made in the dark theme is as readable as a black
 * one; the eraser paints white, like it does over the canvas.
 */
object DrawingRasterizer {

    private val json = Json { ignoreUnknownKeys = true }

    /** The margin around the strokes, the same as the preview of the drawing in the editor. */
    const val PADDING = 40f
    const val MAX_SIDE = 1024
    const val MIN_SIDE = 256

    /** The PNG of the strokes in [drawingJson], or null when the JSON holds no strokes to draw. */
    fun toPng(drawingJson: String): ByteArray? {
        val drawing = try {
            json.decodeFromString(DrawingData.serializer(), drawingJson)
        } catch (e: Exception) {
            return null
        }

        return toPng(drawing)
    }

    fun toPng(drawing: DrawingData): ByteArray? {
        val strokes = drawing.strokes.filter { stroke -> stroke.points.isNotEmpty() }
        if (strokes.none { stroke -> stroke.tool != DrawingTool.ERASER }) return null

        val points = strokes.flatMap { stroke -> stroke.points }
        val minX = points.minOf { point -> point.x } - PADDING
        val maxX = points.maxOf { point -> point.x } + PADDING
        val minY = points.minOf { point -> point.y } - PADDING
        val maxY = points.maxOf { point -> point.y } + PADDING

        val width = (maxX - minX).coerceAtLeast(1f)
        val height = (maxY - minY).coerceAtLeast(1f)
        val longest = maxOf(width, height)
        val scale = when {
            longest > MAX_SIDE -> MAX_SIDE / longest
            longest < MIN_SIDE -> MIN_SIDE / longest
            else -> 1f
        }

        val image = BufferedImage(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            BufferedImage.TYPE_INT_RGB
        )
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)

            strokes.forEach { stroke ->
                graphics.color = colorOf(stroke)
                graphics.stroke = BasicStroke(
                    (stroke.strokeWidth * scale * widthFactorOf(stroke)).coerceAtLeast(1f),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
                )
                graphics.draw(pathOf(stroke, minX, minY, scale))
            }
        } finally {
            graphics.dispose()
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }

    private fun pathOf(stroke: Stroke, minX: Float, minY: Float, scale: Float): Path2D.Float {
        val path = Path2D.Float()
        stroke.points.forEachIndexed { index, point ->
            val x = (point.x - minX) * scale
            val y = (point.y - minY) * scale
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // A single tap still leaves a dot.
        if (stroke.points.size == 1) {
            val point = stroke.points.first()
            path.lineTo((point.x - minX) * scale, (point.y - minY) * scale)
        }
        return path
    }

    private fun colorOf(stroke: Stroke): Color =
        when (stroke.tool) {
            DrawingTool.PEN -> Color.BLACK
            DrawingTool.HIGHLIGHTER -> Color(0xB0, 0xB0, 0xB0)
            DrawingTool.ERASER -> Color.WHITE
        }

    private fun widthFactorOf(stroke: Stroke): Float =
        if (stroke.tool == DrawingTool.HIGHLIGHTER) 3f else 1f
}
