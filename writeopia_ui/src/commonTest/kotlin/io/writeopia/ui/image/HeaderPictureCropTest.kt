package io.writeopia.ui.image

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderPictureCropTest {

    /** A drawing (pixels that differ from every neighbour) on the rows of [strip], soft bands elsewhere. */
    private fun picture(strip: IntRange, band: (y: Int) -> Int = { 128 }): (Int, Int) -> Int =
        { x, y -> if (y in strip) (x * 97 + y * 13) % 256 else band(y) }

    @Test
    fun bandsAboveAndBelowTheDrawingAreCutAway() {
        val rows = HeaderPictureCrop.contentRows(width = 200, height = 400, luma = picture(100 until 300))

        assertEquals(100 until 300, rows)
    }

    @Test
    fun softGradientsCountAsBands() {
        // A blurred band still changes a little from one pixel to the next.
        val luma = picture(strip = 130 until 310) { y -> 40 + y / 4 }

        val rows = HeaderPictureCrop.contentRows(width = 200, height = 400, luma = luma)

        assertEquals(130 until 310, rows)
    }

    @Test
    fun aBandOnOneSideOnlyIsCutAway() {
        val rows = HeaderPictureCrop.contentRows(width = 200, height = 400, luma = picture(0 until 250))

        assertEquals(0 until 250, rows)
    }

    @Test
    fun aPictureWithoutBandsIsKeptWhole() {
        val rows = HeaderPictureCrop.contentRows(width = 200, height = 400, luma = picture(0 until 400))

        assertEquals(0 until 400, rows)
    }

    @Test
    fun aFlatPictureIsKeptWhole() {
        val rows = HeaderPictureCrop.contentRows(width = 200, height = 400) { _, _ -> 128 }

        assertEquals(0 until 400, rows)
    }

    @Test
    fun aTinyStripIsNotTrusted() {
        val rows = HeaderPictureCrop.contentRows(width = 200, height = 400, luma = picture(190 until 200))

        assertEquals(0 until 400, rows)
    }

    @Test
    fun bigPicturesAreSampledAndStillMeasured() {
        val rows = HeaderPictureCrop.contentRows(width = 1344, height = 768, luma = picture(184 until 636))

        assertEquals(184 until 636, rows)
    }

    @Test
    fun theStripCoversANarrowBandByHeight() {
        val placement = HeaderPictureCrop.coverPlacement(
            pictureSize = Size(1344f, 768f),
            strip = 192 until 576,
            bandSize = Size(360f, 230f)
        )

        // 230 / 384 beats 360 / 1344: the strip fills the height and is cropped on the sides.
        assertEquals(230f / 384f, placement.scale, 0.0001f)
        assertEquals((360f - 1344f * placement.scale) / 2, placement.offset.x, 0.01f)
        // The middle of the strip (row 384) lands in the middle of the band.
        assertEquals(115f - 384f * placement.scale, placement.offset.y, 0.01f)
    }

    @Test
    fun theStripCoversAWideBandByWidth() {
        val placement = HeaderPictureCrop.coverPlacement(
            pictureSize = Size(1344f, 768f),
            strip = 0 until 768,
            bandSize = Size(2000f, 230f)
        )

        assertEquals(2000f / 1344f, placement.scale, 0.0001f)
        assertEquals(0f, placement.offset.x, 0.01f)
        assertEquals(115f - 384f * placement.scale, placement.offset.y, 0.01f)
    }
}
