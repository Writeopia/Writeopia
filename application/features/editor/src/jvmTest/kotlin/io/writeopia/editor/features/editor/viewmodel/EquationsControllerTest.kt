package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.ai.Equation
import io.writeopia.ai.EquationAi
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.utils.iterables.Rank
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The backend in memory: answers one equation, or a failure, once its gate opens. */
private class FakeEquations(var failure: String? = null) : EquationAi {
    val recognized = mutableListOf<String>()
    val rendered = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun recognize(drawingJson: String): ResultData<Equation> {
        gate?.await()
        recognized.add(drawingJson)
        return answer("x^2")
    }

    override suspend fun render(latex: String): ResultData<Equation> {
        rendered.add(latex)
        return answer(latex)
    }

    private fun answer(latex: String): ResultData<Equation> =
        failure?.let { message -> ResultData.Error(Exception(message)) }
            ?: ResultData.Complete(Equation(latex, "https://cdn.test/$latex.png"))
}

class EquationsControllerTest {

    private val drawing = StoryStep(id = "drawing", type = StoryTypes.DRAWING.type, text = """{"strokes":[]}""")
    private val equation = StoryStep(id = "equation", type = StoryTypes.EQUATION.type, text = "a+b")

    /** The document as a mutable map, and what `setEquation` was asked. */
    private val stories = mutableMapOf(Rank.at(1) to drawing, Rank.at(2) to equation)
    private val setEquations = mutableListOf<Triple<String, String, String?>>()
    private var scans = 0

    private fun TestScope.controller(equations: FakeEquations?, editable: Boolean = true) = EquationsController(
        scope = backgroundScope,
        resolveEquations = { equations },
        isEditable = { editable },
        stepAt = { position -> stories[position] },
        positionOf = { stepId ->
            scans++
            stories.entries.firstOrNull { (_, step) -> step.id == stepId }?.key
        },
        setEquation = { position, latex, url -> setEquations.add(Triple(position, latex, url)) },
        // The tasks run right away, like the task manager does when nothing else is queued.
        enqueueTask = { _, _, execution -> backgroundScope.launch { execution() } },
        taskIdPrefix = { "editor-doc" },
    )

    @Test
    fun `a drawing becomes the equation the cloud reads, at its own position, and the dialog closes`() =
        runTest(UnconfinedTestDispatcher()) {
            val equations = FakeEquations()
            val controller = controller(equations)

            controller.openDrawingOptions(drawing, Rank.at(1))
            controller.convertDrawing()

            assertEquals(listOf(Triple<String, String, String?>(Rank.at(1), "x^2", "https://cdn.test/x^2.png")), setEquations)
            assertNull(controller.drawingOptions.value)
            assertTrue(controller.convertingDrawingIds.value.isEmpty())
            // The known position still held the drawing: no scan of the document.
            assertEquals(0, scans)
        }

    @Test
    fun `a drawing that moved while the AI read it is found again by its id`() = runTest(UnconfinedTestDispatcher()) {
        val equations = FakeEquations().apply { gate = CompletableDeferred() }
        val controller = controller(equations)

        controller.openDrawingOptions(drawing, Rank.at(1))
        controller.convertDrawing()
        assertEquals(setOf("drawing"), controller.convertingDrawingIds.value)
        assertEquals(DrawingOptionsState.Phase.CONVERTING, controller.drawingOptions.value?.phase)

        stories.remove(Rank.at(1))
        stories[Rank.at(5)] = drawing
        equations.gate?.complete(Unit)

        assertEquals(Rank.at(5), setEquations.single().first)
        assertEquals(1, scans)
    }

    @Test
    fun `a drawing deleted while the AI read it is left alone`() = runTest(UnconfinedTestDispatcher()) {
        val equations = FakeEquations().apply { gate = CompletableDeferred() }
        val controller = controller(equations)

        controller.openDrawingOptions(drawing, Rank.at(1))
        controller.convertDrawing()
        stories.remove(Rank.at(1))
        equations.gate?.complete(Unit)

        assertTrue(setEquations.isEmpty())
        assertTrue(controller.convertingDrawingIds.value.isEmpty())
    }

    @Test
    fun `reopening the dialog during a conversion shows it converting instead of asking again`() =
        runTest(UnconfinedTestDispatcher()) {
            val equations = FakeEquations().apply { gate = CompletableDeferred() }
            val controller = controller(equations)

            controller.openDrawingOptions(drawing, Rank.at(1))
            controller.convertDrawing()
            controller.dismissDrawingOptions()
            controller.openDrawingOptions(drawing, Rank.at(1))
            controller.convertDrawing()

            assertEquals(DrawingOptionsState.Phase.CONVERTING, controller.drawingOptions.value?.phase)
            equations.gate?.complete(Unit)
            assertEquals(1, equations.recognized.size)
        }

    @Test
    fun `a failed reading keeps the drawing and offers a retry`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller(FakeEquations(failure = "No equation was found in the drawing"))

        controller.openDrawingOptions(drawing, Rank.at(1))
        controller.convertDrawing()

        assertTrue(setEquations.isEmpty())
        assertEquals(DrawingOptionsState.Phase.FAILED, controller.drawingOptions.value?.phase)
        assertEquals("No equation was found in the drawing", controller.drawingOptions.value?.error)
    }

    @Test
    fun `without the cloud the dialog says so`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller(equations = null)

        controller.openDrawingOptions(drawing, Rank.at(1))
        controller.convertDrawing()

        assertEquals(DrawingOptionsState.Phase.UNAVAILABLE, controller.drawingOptions.value?.phase)
    }

    @Test
    fun `editing an equation renders the new LaTeX and closes the dialog`() = runTest(UnconfinedTestDispatcher()) {
        val equations = FakeEquations()
        val controller = controller(equations)

        controller.edit(equation, Rank.at(2))
        controller.onLatexChange(" \\frac{a}{b} ")
        controller.saveEdition()

        assertEquals(listOf("\\frac{a}{b}"), equations.rendered)
        assertEquals(Triple(Rank.at(2), "\\frac{a}{b}", "https://cdn.test/\\frac{a}{b}.png"), setEquations.single())
        assertNull(controller.edition.value)
    }

    @Test
    fun `a LaTeX the backend refuses stays in the dialog with the error`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller(FakeEquations(failure = "Invalid LaTeX"))

        controller.edit(equation, Rank.at(2))
        controller.onLatexChange("\\frac{1}{")
        controller.saveEdition()

        assertTrue(setEquations.isEmpty())
        assertEquals("Invalid LaTeX", controller.edition.value?.error)
        assertEquals(false, controller.edition.value?.rendering)
    }

    @Test
    fun `without the cloud an edited equation keeps its LaTeX as text`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller(equations = null)

        controller.edit(equation, Rank.at(2))
        controller.onLatexChange("x")
        controller.saveEdition()

        assertEquals(Triple(Rank.at(2), "x", null), setEquations.single())
    }

    @Test
    fun `a locked document converts and edits nothing`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller(FakeEquations(), editable = false)

        controller.openDrawingOptions(drawing, Rank.at(1))
        controller.edit(equation, Rank.at(2))

        assertNull(controller.drawingOptions.value)
        assertNull(controller.edition.value)
    }
}
