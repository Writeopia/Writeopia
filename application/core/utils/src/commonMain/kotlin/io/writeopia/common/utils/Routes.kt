package io.writeopia.common.utils

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey

// Simple routes (no arguments)
@Serializable
data object MainAppRoute : Route

@Serializable
data object StartAppRoute : Route

@Serializable
data object SearchRoute : Route

@Serializable
data object NotificationsRoute : Route

@Serializable
data object AccountRoute : Route

@Serializable
data object ForceGraphRoute : Route

// Routes with arguments
@Serializable
data class EditorRoute(
    val noteId: String? = null,
    val noteTitle: String? = null,
    val parentFolderId: String = "root"
) : Route

@Serializable
data class PresentationRoute(val documentId: String) : Route

@Serializable
data class ChooseNoteRoute(
    val navigationType: String = "ROOT",
    val navigationPath: String = ""
) : Route

@Serializable
data class DrawingRoute(
    val documentId: String,
    val storyStepId: String? = null,
    val drawingJson: String? = null
) : Route

@Serializable
data class EditFolderRoute(val folderId: String) : Route

// Auth routes
@Serializable
data object AuthMenuInnerNavigationRoute : Route

@Serializable
data object AuthMenuRoute : Route

@Serializable
data object AuthRegisterRoute : Route

@Serializable
data object AuthResetPasswordRoute : Route

@Serializable
data object EmailConfirmRoute : Route

@Serializable
data object ChooseWorkspaceRoute : Route

@Serializable
data object DesktopAuthRoute : Route
