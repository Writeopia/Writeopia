package io.writeopia.api.ai.equation

import io.writeopia.sdk.models.story.EquationImage
import org.scilab.forge.jlatexmath.ParseException
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** LaTeX that JLaTeXMath could not parse. */
class InvalidLatexException(latex: String, cause: Throwable) :
    IllegalArgumentException("Invalid LaTeX: ${cause.message ?: latex}", cause)

/**
 * Renders LaTeX math to a PNG with JLaTeXMath: black glyphs on a transparent background, in
 * display style, at [EquationImage.SCALE] pixels per point of [EquationImage.FONT_SIZE_POINTS], so
 * the apps show it at a third of its pixel size and it stays crisp on dense screens. The glyphs
 * are black so the apps can tint them with the text color of the theme.
 */
object LatexRenderer {

    init {
        // There is no display on the servers; Java2D draws into memory.
        System.setProperty("java.awt.headless", "true")
    }

    const val CONTENT_TYPE = "image/png"

    /**
     * The PNG of [latex], written as math with no delimiters.
     * @throws InvalidLatexException when [latex] does not parse.
     */
    fun toPng(latex: String): ByteArray {
        // JLaTeXMath draws an unclosed group as if it were closed; an equation with one is a
        // mistake, from the model or the user, and better refused than half drawn.
        if (!bracesBalanced(latex)) {
            throw InvalidLatexException(latex, IllegalArgumentException("Unbalanced braces"))
        }

        val formula = try {
            TeXFormula(latex)
        } catch (e: ParseException) {
            throw InvalidLatexException(latex, e)
        } catch (e: RuntimeException) {
            // JLaTeXMath throws its own hierarchy (JMathTeXException) for unknown symbols too.
            throw InvalidLatexException(latex, e)
        }

        val image = try {
            formula.createBufferedImage(
                TeXConstants.STYLE_DISPLAY,
                EquationImage.FONT_SIZE_POINTS * EquationImage.SCALE,
                Color.BLACK,
                null
            ) as BufferedImage
        } catch (e: RuntimeException) {
            throw InvalidLatexException(latex, e)
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }

    /** Whether every `{` of [latex] has its `}` (escaped `\{` and `\}` are symbols). */
    fun bracesBalanced(latex: String): Boolean {
        var depth = 0
        var index = 0
        while (index < latex.length) {
            when (latex[index]) {
                '\\' -> index++
                '{' -> depth++
                '}' -> if (--depth < 0) return false
            }
            index++
        }
        return depth == 0
    }
}
