package io.writeopia.ui.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs
import kotlin.math.max

/**
 * Finds the rows of a header picture that hold the drawing. The image model paints a wide scene
 * across the middle of the frame and fills the rest with blurred bands, whose height changes from
 * one picture to the next, so the band of the header shows the drawing and never those bands.
 * The native app has the same rules in `HeaderPictureCrop.swift`.
 */
object HeaderPictureCrop {
    /** The picture is sampled on a grid of about this many rows and columns, whatever its size. */
    private const val SAMPLED_ROWS = 384
    private const val SAMPLED_COLUMNS = 336

    /** A row holds drawing when it is sharper than this share of the sharp rows of the picture. */
    private const val SHARE_OF_SHARP = 0.3

    /** Below this sharpness the whole picture is flat (a gradient, a plain color): no bands. */
    private const val FLAT = 0.5

    /** A strip shorter than this share of the picture is a wrong guess: the whole picture is used. */
    private const val MIN_STRIP = 0.2

    /**
     * The rows of the drawing, from the first one to the one after the last, read from the
     * brightness of the pixels: [luma] gives 0..255 for a pixel. Blurred bands change little from
     * one pixel to the next, the drawing changes a lot.
     */
    fun contentRows(width: Int, height: Int, luma: (x: Int, y: Int) -> Int): IntRange {
        val whole = 0 until height
        if (width < 2 || height < 2) return whole

        val rowStep = max(1, height / SAMPLED_ROWS)
        val columnStep = max(1, width / SAMPLED_COLUMNS)
        val rows = (0 until height step rowStep).toList()
        val sharpness = DoubleArray(rows.size) { i ->
            val y = rows[i]
            var sum = 0L
            var count = 0
            var x = columnStep
            while (x < width) {
                sum += abs(luma(x, y) - luma(x - columnStep, y))
                count++
                x += columnStep
            }
            if (count == 0) 0.0 else sum.toDouble() / count
        }

        val sorted = sharpness.sortedArray()
        val reference = sorted[(sorted.size - 1) * 9 / 10]
        if (reference < FLAT) return whole

        val threshold = reference * SHARE_OF_SHARP
        val first = sharpness.indexOfFirst { it > threshold }
        val last = sharpness.indexOfLast { it > threshold }
        if (first < 0) return whole

        val top = rows[first]
        val bottom = minOf(height, rows[last] + rowStep)
        if (bottom - top < height * MIN_STRIP) return whole

        return top until bottom
    }

    /** The rows of the drawing of [bitmap]; reads every pixel, so call it off the main thread. */
    fun contentRows(bitmap: ImageBitmap): IntRange {
        val pixels = bitmap.toPixelMap()
        return contentRows(pixels.width, pixels.height) { x, y ->
            val color = pixels[x, y]
            ((color.red * 299 + color.green * 587 + color.blue * 114) * 255 / 1000).toInt()
        }
    }

    /** How a picture is drawn in a band: the whole picture scaled by [scale], moved by [offset]. */
    data class Placement(val scale: Float, val offset: Offset)

    /**
     * Where to draw a picture of [pictureSize] in a band of [bandSize] so that its rows [strip]
     * cover the band, centered, with the proportions kept: what spills over the band is cropped.
     */
    fun coverPlacement(pictureSize: Size, strip: IntRange, bandSize: Size): Placement {
        val stripTop = strip.first.toFloat()
        val stripHeight = (strip.last + 1 - strip.first).toFloat()
        val scale = max(bandSize.width / pictureSize.width, bandSize.height / stripHeight)
        val x = (bandSize.width - pictureSize.width * scale) / 2
        val y = bandSize.height / 2 - (stripTop + stripHeight / 2) * scale
        return Placement(scale, Offset(x, y))
    }
}
