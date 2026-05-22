package io.writeopia.ui.model

import io.writeopia.sdk.model.story.Selection

/**
 * The class holds the information of the content to be draw by the SDK.
 *
 * @param editable if the content can the edited
 * @param focusId which content has the focus
 * @param position the position of the content
 * @param extraData additional data
 * @param selectMode if the content is current being selected and should show a visible feedback of it.
 */
data class DrawInfo(
    val editable: Boolean = true,
    val focus: Double? = null,
    val position: Double = 0.0,
    val selectMode: Boolean = false,
    val selection: Selection? = null,
    val extraData: Map<String, Any> = emptyMap()
) {
    fun hasFocus(): Boolean = position == focus
}
