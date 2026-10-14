package io.writeopia.ui.utils

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class AboveAnchorPositionProviderTest {

    private val window = IntSize(width = 1000, height = 2000)
    private val popup = IntSize(width = 300, height = 100)

    private fun position(anchor: IntRect): IntOffset =
        AboveAnchorPositionProvider.calculatePosition(anchor, window, LayoutDirection.Ltr, popup)

    @Test
    fun `the popup sits right above the anchor`() {
        val anchor = IntRect(left = 100, top = 500, right = 400, bottom = 540)

        assertEquals(IntOffset(x = 100, y = 400), position(anchor))
    }

    @Test
    fun `the popup goes below the anchor when it does not fit above`() {
        val anchor = IntRect(left = 100, top = 50, right = 400, bottom = 90)

        assertEquals(IntOffset(x = 100, y = 90), position(anchor))
    }

    @Test
    fun `the popup stays inside the window sideways`() {
        val anchor = IntRect(left = 900, top = 500, right = 1000, bottom = 540)

        assertEquals(IntOffset(x = 700, y = 400), position(anchor))
    }

    @Test
    fun `the popup stays inside the window at the bottom`() {
        val anchor = IntRect(left = 100, top = 50, right = 400, bottom = 1980)

        assertEquals(IntOffset(x = 100, y = 1900), position(anchor))
    }
}
