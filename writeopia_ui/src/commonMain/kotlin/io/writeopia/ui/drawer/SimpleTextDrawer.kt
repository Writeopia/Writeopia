package io.writeopia.ui.drawer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import io.writeopia.ui.model.DrawInfo
import io.writeopia.sdk.models.story.StoryStep

interface SimpleTextDrawer {

    var onFocusChanged: (Double, FocusState) -> Unit

    /**
     * Draws the StoryStep including its [DrawInfo]
     *
     * @param step [StoryStep]
     * @param drawInfo [DrawInfo]
     */
    @Composable
    fun Text(
        step: StoryStep,
        drawInfo: DrawInfo,
        interactionSource: MutableInteractionSource,
        focusRequester: FocusRequester?,
        decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit
    )
}
