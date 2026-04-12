package io.writeopia.drawing.ui.drawer

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.model.DrawConfig

/**
 * Factory for creating drawing-related drawers.
 */
object DrawingDrawerFactory {

    /**
     * Creates a map containing the DrawingPreviewDrawer for DRAWING story type.
     * This should be merged with other drawers when setting up the WriteopiaEditor.
     */
    fun createDrawingDrawers(
        onDrawingClick: (StoryStep, Int) -> Unit,
        onDelete: (Action.DeleteStory) -> Unit,
        drawConfig: DrawConfig,
        onSelected: (Boolean, Int) -> Unit = { _, _ -> },
        onDragStart: () -> Unit = {},
        onDragStop: () -> Unit = {},
    ): Map<Int, StoryStepDrawer> {
        return mapOf(
            StoryTypes.DRAWING.type.number to DrawingPreviewDrawer(
                onDrawingClick = onDrawingClick,
                onDelete = onDelete,
                drawConfig = drawConfig,
                onSelected = onSelected,
                onDragStart = onDragStart,
                onDragStop = onDragStop
            )
        )
    }
}
