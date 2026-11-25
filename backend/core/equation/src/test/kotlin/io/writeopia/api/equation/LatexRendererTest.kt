package io.writeopia.api.equation

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LatexRendererTest {

    @Test
    fun `a formula renders to a transparent PNG with opaque glyphs`() {
        val png = LatexRenderer.toPng("\\int_0^1 \\frac{x^2}{2} \\, dx = \\frac{1}{6}")
        val image = ImageIO.read(ByteArrayInputStream(png))

        assertTrue(image.width > 50 && image.height > 20)
        assertEquals(0, image.getRGB(0, 0) ushr 24, "the corner is transparent")

        val opaque = (0 until image.width).flatMap { x -> (0 until image.height).map { y -> x to y } }
            .count { (x, y) -> (image.getRGB(x, y) ushr 24) > 200 }
        assertTrue(opaque > 100, "the glyphs are drawn")
    }

    @Test
    fun `LaTeX that does not parse is refused`() {
        assertFailsWith<InvalidLatexException> { LatexRenderer.toPng("\\frac{1}{") }
        assertFailsWith<InvalidLatexException> { LatexRenderer.toPng("x}") }
        assertFailsWith<InvalidLatexException> { LatexRenderer.toPng("\\notacommand{x}") }
        assertFailsWith<InvalidLatexException> { LatexRenderer.toPng("\\left( x") }
    }

    @Test
    fun `escaped braces are symbols, not groups`() {
        assertTrue(LatexRenderer.bracesBalanced("\\{ x \\}"))
        assertTrue(LatexRenderer.bracesBalanced("\\frac{1}{2}"))
        assertTrue(LatexRenderer.toPng("\\{ 1, 2 \\}").isNotEmpty())
    }

    @Test
    fun `a formula wider than a screen is refused before it is drawn`() {
        assertFailsWith<InvalidLatexException> { LatexRenderer.toPng("x+".repeat(400) + "x") }
    }
}
