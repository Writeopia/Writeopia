package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.ai.Equation
import io.writeopia.ai.EquationAi
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The equations of the editor: a drawing turned into one by the cloud AI (the "..." dialog of
 * the drawing), and the LaTeX of one edited by hand (the dialog of the pencil). The backend
 * renders every LaTeX to a picture; without it the step keeps the LaTeX as text.
 */
class EquationsController(
    private val scope: CoroutineScope,
    /** The cloud, when it can answer right now; null outside an online workspace. */
    private val resolveEquations: suspend () -> EquationAi?,
    private val isEditable: () -> Boolean,
    /** The step at a position of the document, as it is now. */
    private val stepAt: (position: String) -> StoryStep?,
    /**
     * The position of a step found by its id: a scan of the whole document. Only a fallback for
     * a step that moved while the AI read it; the known position is checked first.
     */
    private val positionOf: (stepId: String) -> String?,
    /** Makes the step at the position the equation, with its picture (null while there is none). */
    private val setEquation: (position: String, latex: String, imageUrl: String?) -> Unit,
    /** Runs the recognition as an AI task of the editor, so it shows with the others. */
    private val enqueueTask: (id: String, description: String, execution: suspend () -> Result<Unit>) -> Unit,
    private val taskIdPrefix: () -> String,
) {
    private val _drawingOptions = MutableStateFlow<DrawingOptionsState?>(null)
    val drawingOptions: StateFlow<DrawingOptionsState?> = _drawingOptions.asStateFlow()

    private val _convertingDrawingIds = MutableStateFlow<Set<String>>(emptySet())
    val convertingDrawingIds: StateFlow<Set<String>> = _convertingDrawingIds.asStateFlow()

    private val _edition = MutableStateFlow<EquationEditionState?>(null)
    val edition: StateFlow<EquationEditionState?> = _edition.asStateFlow()

    // Drawings to equations

    fun openDrawingOptions(step: StoryStep, position: String) {
        if (!isEditable()) return
        _drawingOptions.value = DrawingOptionsState(step, position)
    }

    /** The conversion goes on in the background: the preview shows it and the result lands anyway. */
    fun dismissDrawingOptions() {
        _drawingOptions.value = null
    }

    /**
     * The cloud AI reads the strokes of the drawing of the open dialog and the step becomes the
     * equation it wrote, at the same position, so undo brings the drawing back. The dialog
     * follows the work and closes when it is done.
     */
    fun convertDrawing() {
        val options = _drawingOptions.value ?: return
        if (!isEditable() || options.phase == DrawingOptionsState.Phase.CONVERTING) return

        // The dialog may have been closed and opened again while the AI still reads this drawing.
        if (options.step.id in _convertingDrawingIds.value) {
            updateOptions(options) { it.copy(phase = DrawingOptionsState.Phase.CONVERTING, error = null) }
            return
        }

        val drawingJson = options.step.text
        if (drawingJson.isNullOrBlank()) {
            updateOptions(options) { it.copy(phase = DrawingOptionsState.Phase.FAILED, error = null) }
            return
        }

        scope.launch {
            val equations = resolveEquations()
            if (equations == null) {
                updateOptions(options) { it.copy(phase = DrawingOptionsState.Phase.UNAVAILABLE) }
                return@launch
            }

            updateOptions(options) { it.copy(phase = DrawingOptionsState.Phase.CONVERTING, error = null) }
            _convertingDrawingIds.update { ids -> ids + options.step.id }

            enqueueTask("${taskIdPrefix()}-equation-${options.step.id}", "Reading the equation...") {
                val result = try {
                    equations.recognize(drawingJson)
                } finally {
                    // Cleared whatever happened, so the preview never keeps its progress.
                    _convertingDrawingIds.update { ids -> ids - options.step.id }
                }
                applyRecognition(options, result)
            }
        }
    }

    private fun applyRecognition(options: DrawingOptionsState, result: ResultData<Equation>): Result<Unit> =
        when (result) {
            is ResultData.Complete -> {
                // The drawing left the document meanwhile: nothing to replace.
                val position = positionOfDrawing(options) ?: return Result.success(Unit)
                setEquation(position, result.data.latex, result.data.imageUrl)
                updateOptions(options) { null }
                Result.success(Unit)
            }

            else -> {
                val message = (result as? ResultData.Error)?.exception?.message
                updateOptions(options) { it.copy(phase = DrawingOptionsState.Phase.FAILED, error = message) }
                Result.failure(Exception(message ?: "The equation could not be read"))
            }
        }

    /**
     * Where the drawing of [options] is now: at the position it had, unless another step took
     * it (the drawing moved while the AI read it), in which case the document is scanned for it.
     */
    private fun positionOfDrawing(options: DrawingOptionsState): String? =
        options.position.takeIf { position -> stepAt(position)?.id == options.step.id }
            ?: positionOf(options.step.id)

    /** Changes the dialog only while it still shows the drawing of [options]. */
    private fun updateOptions(
        options: DrawingOptionsState,
        change: (DrawingOptionsState) -> DrawingOptionsState?
    ) {
        _drawingOptions.update { current -> if (current?.step?.id == options.step.id) change(current) else current }
    }

    // Editing equations

    /** Opens the dialog with the LaTeX of the equation at [position]. */
    fun edit(step: StoryStep, position: String) {
        if (!isEditable()) return
        _edition.value = EquationEditionState(position, step.text ?: "")
    }

    fun onLatexChange(latex: String) {
        _edition.update { state -> state?.copy(latex = latex, error = null) }
    }

    fun dismissEdition() {
        _edition.value = null
    }

    /** Renders the LaTeX of the dialog and puts it in its equation; the dialog closes once done. */
    fun saveEdition() {
        val edition = _edition.value ?: return
        if (!isEditable() || edition.rendering) return
        val latex = edition.latex.trim()
        if (latex.isEmpty()) return

        scope.launch {
            _edition.update { state -> state?.copy(rendering = true, error = null) }
            val error = render(edition.position, latex)
            _edition.update { state -> if (state == null || error == null) null else state.copy(rendering = false, error = error) }
        }
    }

    /**
     * Renders [latex] with the backend and puts it, with its picture, in the step at [position].
     * Without the cloud, the step keeps the LaTeX as text and no picture.
     * @return null when the step was updated, else the error to show.
     */
    suspend fun render(position: String, latex: String): String? {
        val trimmed = latex.trim()
        val equations = resolveEquations()
        if (equations == null) {
            setEquation(position, trimmed, null)
            return null
        }

        return when (val result = equations.render(trimmed)) {
            is ResultData.Complete -> {
                setEquation(position, result.data.latex, result.data.imageUrl)
                null
            }

            is ResultData.Error -> result.exception?.message ?: RENDER_FAILED
            else -> RENDER_FAILED
        }
    }

    companion object {
        private const val RENDER_FAILED = "The equation could not be rendered"
    }
}
