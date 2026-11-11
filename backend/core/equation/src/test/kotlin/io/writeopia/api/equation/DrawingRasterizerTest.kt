package io.writeopia.api.equation

import io.writeopia.sdk.models.drawing.DrawPoint
import io.writeopia.sdk.models.drawing.DrawingData
import io.writeopia.sdk.models.drawing.DrawingTool
import io.writeopia.sdk.models.drawing.Stroke
import kotlinx.serialization.json.Json
import java.awt.Color
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawingRasterizerTest {

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, tool: DrawingTool = DrawingTool.PEN, color: Int = 0xFF000000.toInt()) =
        Stroke(points = listOf(DrawPoint(x0, y0), DrawPoint(x1, y1)), color = color, strokeWidth = 8f, tool = tool)

    @Test
    fun `the strokes are drawn in black on white, cropped to their bounds with the margin`() {
        val drawing = DrawingData(strokes = listOf(line(100f, 200f, 300f, 200f, color = 0xFFFFFFFF.toInt())))

        val png = assertNotNull(DrawingRasterizer.toPng(drawing))
        val image = ImageIO.read(ByteArrayInputStream(png))

        // 200 wide + 2 * 40 padding is over the minimum side, so it is kept as it is.
        assertEquals((200 + 2 * DrawingRasterizer.PADDING).toInt(), image.width)
        assertEquals((2 * DrawingRasterizer.PADDING).toInt(), image.height)

        assertEquals(Color.WHITE.rgb, image.getRGB(1, 1))
        val middle = image.getRGB(image.width / 2, image.height / 2)
        assertTrue(Color(middle).red < 50, "a white stroke is still drawn as ink")
    }

    @Test
    fun `a small drawing is scaled up to the minimum side`() {
        val drawing = DrawingData(strokes = listOf(line(0f, 0f, 20f, 10f)))

        val image = ImageIO.read(ByteArrayInputStream(assertNotNull(DrawingRasterizer.toPng(drawing))))

        assertEquals(DrawingRasterizer.MIN_SIDE, image.width)
    }

    @Test
    fun `a wide drawing is scaled down to the longest side`() {
        val drawing = DrawingData(strokes = listOf(line(0f, 0f, 4000f, 1000f)))

        val image = ImageIO.read(ByteArrayInputStream(assertNotNull(DrawingRasterizer.toPng(drawing))))

        assertEquals(DrawingRasterizer.MAX_SIDE, image.width)
    }

    @Test
    fun `a drawing with only eraser strokes or no strokes gives no picture`() {
        assertNull(DrawingRasterizer.toPng(DrawingData()))
        assertNull(DrawingRasterizer.toPng(DrawingData(strokes = listOf(line(0f, 0f, 10f, 10f, tool = DrawingTool.ERASER)))))
        assertNull(DrawingRasterizer.toPng("not json"))
    }

    @Test
    fun `the JSON of a drawing is read as the apps write it`() {
        val json = Json.encodeToString(DrawingData.serializer(), DrawingData(strokes = listOf(line(0f, 0f, 50f, 50f))))

        assertNotNull(DrawingRasterizer.toPng(json))
    }
}
