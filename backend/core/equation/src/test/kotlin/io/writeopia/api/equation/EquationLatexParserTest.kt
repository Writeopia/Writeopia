package io.writeopia.api.equation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EquationLatexParserTest {

    @Test
    fun `the LaTeX is read from the JSON the model is asked for`() {
        assertEquals("x^2 + 1", EquationLatexParser.parse("""{"latex": "x^2 + 1"}"""))
        assertEquals("\\frac{a}{b}", EquationLatexParser.parse("""{"latex": "\\frac{a}{b}"}"""))
    }

    @Test
    fun `code fences and math delimiters are stripped`() {
        assertEquals("x^2", EquationLatexParser.parse("```json\n{\"latex\": \"\$\$x^2\$\$\"}\n```"))
        assertEquals("x^2", EquationLatexParser.parse("```latex\n\\[ x^2 \\]\n```"))
        assertEquals("x^2", EquationLatexParser.parse("\$x^2\$"))
        assertEquals("x^2", EquationLatexParser.parse("\\( x^2 \\)"))
        assertEquals("x^2", EquationLatexParser.parse("\\begin{equation}x^2\\end{equation}"))
    }

    @Test
    fun `an empty answer means there is no equation`() {
        assertNull(EquationLatexParser.parse(null))
        assertNull(EquationLatexParser.parse("  "))
        assertNull(EquationLatexParser.parse("""{"latex": ""}"""))
        assertNull(EquationLatexParser.parse("\$\$"))
    }

    @Test
    fun `plain text is kept when the model did not answer JSON`() {
        assertEquals("\\sum_{i=1}^n i", EquationLatexParser.parse("\\sum_{i=1}^n i"))
    }
}
