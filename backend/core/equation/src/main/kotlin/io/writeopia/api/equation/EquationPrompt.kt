package io.writeopia.api.equation

/** What the model is told when it reads the equation in the picture of a drawing. */
object EquationPrompt {
    val text: String =
        "The picture shows handwritten mathematics. Transcribe it as LaTeX math, exactly as written, " +
            "without solving, simplifying or completing it. Use standard commands: \\frac for " +
            "fractions, ^ and _ for exponents and indices, \\sqrt, \\int (with limits as _ and ^), " +
            "\\sum, \\prod, \\lim, \\frac{d}{dx} and \\partial for derivatives, \\infty, \\cdot, \\times, " +
            "\\leq, \\geq, \\neq, \\approx, \\pm, Greek letters like \\alpha and \\pi, \\sin, \\cos, \\log, " +
            "\\ln, \\left( \\right) for tall brackets, and \\begin{pmatrix} for matrices. Several lines " +
            "become one \\begin{aligned} block with \\\\ between lines. Do not add delimiters like $ or " +
            "\\[, text or explanations. Answer only with JSON of the form {\"latex\": \"...\"}; when the " +
            "picture holds no mathematics, answer {\"latex\": \"\"}."

    /** Appended to [text] when the first answer was LaTeX the renderer could not parse. */
    fun retry(invalidLatex: String): String =
        "$text\n\nA previous answer, \"$invalidLatex\", was not valid LaTeX and could not be rendered. " +
            "Answer again with LaTeX that compiles, using only the commands listed above."
}
