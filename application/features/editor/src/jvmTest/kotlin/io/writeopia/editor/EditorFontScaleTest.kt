package io.writeopia.editor

import io.writeopia.editor.features.editor.viewmodel.EditorFontScale
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorFontScaleTest {

    @Test
    fun `larger and smaller move by a tenth`() {
        assertEquals(1.1F, EditorFontScale.larger(EditorFontScale.DEFAULT))
        assertEquals(0.9F, EditorFontScale.smaller(EditorFontScale.DEFAULT))
    }

    @Test
    fun `going up and back down returns exactly to the default`() {
        var scale = EditorFontScale.DEFAULT
        repeat(7) { scale = EditorFontScale.larger(scale) }
        repeat(7) { scale = EditorFontScale.smaller(scale) }
        assertEquals(EditorFontScale.DEFAULT, scale)
    }

    @Test
    fun `the scale stays within its bounds`() {
        var scale = EditorFontScale.DEFAULT
        repeat(100) { scale = EditorFontScale.larger(scale) }
        assertEquals(EditorFontScale.MAX, scale)
        repeat(100) { scale = EditorFontScale.smaller(scale) }
        assertEquals(EditorFontScale.MIN, scale)
    }
}
