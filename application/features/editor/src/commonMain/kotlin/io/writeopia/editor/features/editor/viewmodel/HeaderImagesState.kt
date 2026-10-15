package io.writeopia.editor.features.editor.viewmodel

/** The header pictures in the header menu: the ones of the workspace and the one being drawn. */
data class HeaderImagesState(
    /** False outside an online workspace, where the cloud can't draw: the button is hidden. */
    val canGenerate: Boolean = false,
    /** The approved pictures of the workspace, the newest first. */
    val images: List<String> = emptyList(),
    val generation: HeaderImageGeneration = HeaderImageGeneration.Idle,
)

/** A header the AI draws, shown to the user to approve or to draw again. */
sealed class HeaderImageGeneration {
    data object Idle : HeaderImageGeneration()

    data object Generating : HeaderImageGeneration()

    /**
     * A drawn picture, already on the CDN, waiting for the user to approve it. While [isSaving]
     * it is being kept with the workspace. [saveFailed] when that failed, to approve again;
     * [saveError] says why when the backend said so.
     */
    data class Candidate(
        val url: String,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        val saveError: String? = null,
    ) : HeaderImageGeneration()

    /** The AI drew nothing; [message] says why when the backend said so. */
    data class Failed(val message: String?) : HeaderImageGeneration()
}
