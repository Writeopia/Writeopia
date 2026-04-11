package io.writeopia.drawing.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.writeopia.drawing.di.DrawingInjection
import io.writeopia.drawing.ui.screen.DrawingScreen
import io.writeopia.sdk.models.drawing.DrawingData

const val DRAWING_ROUTE = "drawing"
const val DRAWING_DOCUMENT_ID_ARG = "documentId"
const val DRAWING_STEP_ID_ARG = "storyStepId"
const val DRAWING_JSON_ARG = "drawingJson"

fun NavGraphBuilder.drawingNavigation(
    drawingInjection: DrawingInjection,
    navigateBack: () -> Unit,
    onDrawingSaved: (String, String, DrawingData) -> Unit // documentId, storyStepId, drawingData
) {
    composable(
        route = "$DRAWING_ROUTE/{$DRAWING_DOCUMENT_ID_ARG}?$DRAWING_STEP_ID_ARG={$DRAWING_STEP_ID_ARG}&$DRAWING_JSON_ARG={$DRAWING_JSON_ARG}",
        arguments = listOf(
            navArgument(DRAWING_DOCUMENT_ID_ARG) { type = NavType.StringType },
            navArgument(DRAWING_STEP_ID_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(DRAWING_JSON_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        val documentId = backStackEntry.savedStateHandle.get<String?>(DRAWING_DOCUMENT_ID_ARG) ?: ""
        val storyStepId = backStackEntry.savedStateHandle.get<String?>(DRAWING_STEP_ID_ARG)
        val initialJson = backStackEntry.savedStateHandle.get<String?>(DRAWING_JSON_ARG)

        val viewModel = drawingInjection.provideDrawingViewModel()

        DrawingScreen(
            viewModel = viewModel,
            initialDrawingJson = initialJson,
            onSave = { drawingData ->
                onDrawingSaved(documentId, storyStepId ?: "", drawingData)
                navigateBack()
            },
            onCancel = navigateBack
        )
    }
}

/**
 * Navigate to the drawing screen to create a new drawing.
 */
fun NavController.navigateToDrawing(documentId: String) {
    navigate("$DRAWING_ROUTE/$documentId")
}

/**
 * Navigate to the drawing screen to edit an existing drawing.
 */
fun NavController.navigateToDrawing(documentId: String, storyStepId: String, drawingJson: String?) {
    val encodedJson = drawingJson?.let {
        // URL encode special characters
        it.replace("%", "%25")
            .replace("/", "%2F")
            .replace("?", "%3F")
            .replace("&", "%26")
            .replace("=", "%3D")
    }
    val route = if (encodedJson != null) {
        "$DRAWING_ROUTE/$documentId?$DRAWING_STEP_ID_ARG=$storyStepId&$DRAWING_JSON_ARG=$encodedJson"
    } else {
        "$DRAWING_ROUTE/$documentId?$DRAWING_STEP_ID_ARG=$storyStepId"
    }
    navigate(route)
}
