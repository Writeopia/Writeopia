package io.writeopia.drawing.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.common.utils.DrawingRoute

/**
 * Navigate to the drawing screen to create a new drawing.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToDrawing(documentId: String) {
    add(DrawingRoute(documentId = documentId))
}

/**
 * Navigate to the drawing screen to edit an existing drawing.
 * Updated for Navigation 3.
 */
fun NavBackStack<NavKey>.navigateToDrawing(documentId: String, storyStepId: String, drawingJson: String?) {
    add(DrawingRoute(
        documentId = documentId,
        storyStepId = storyStepId,
        drawingJson = drawingJson
    ))
}
