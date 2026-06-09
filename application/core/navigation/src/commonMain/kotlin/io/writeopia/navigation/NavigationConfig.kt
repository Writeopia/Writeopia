package io.writeopia.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import io.writeopia.common.utils.AccountRoute
import io.writeopia.common.utils.AuthMenuInnerNavigationRoute
import io.writeopia.common.utils.AuthMenuRoute
import io.writeopia.common.utils.AuthRegisterRoute
import io.writeopia.common.utils.AuthResetPasswordRoute
import io.writeopia.common.utils.ChooseNoteRoute
import io.writeopia.common.utils.ChooseWorkspaceRoute
import io.writeopia.common.utils.DesktopAuthRoute
import io.writeopia.common.utils.DrawingRoute
import io.writeopia.common.utils.EditFolderRoute
import io.writeopia.common.utils.EditorRoute
import io.writeopia.common.utils.EmailConfirmRoute
import io.writeopia.common.utils.ForceGraphRoute
import io.writeopia.common.utils.MainAppRoute
import io.writeopia.common.utils.NotificationsRoute
import io.writeopia.common.utils.PresentationRoute
import io.writeopia.common.utils.Route
import io.writeopia.common.utils.SearchRoute
import io.writeopia.common.utils.StartAppRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Serialization configuration for Navigation 3 routes.
 * This registers all Route subclasses for polymorphic serialization.
 * Required for multiplatform state saving (iOS, web, desktop).
 */
val navigationConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(MainAppRoute::class)
            subclass(StartAppRoute::class)
            subclass(SearchRoute::class)
            subclass(NotificationsRoute::class)
            subclass(AccountRoute::class)
            subclass(ForceGraphRoute::class)
            subclass(EditorRoute::class)
            subclass(PresentationRoute::class)
            subclass(ChooseNoteRoute::class)
            subclass(DrawingRoute::class)
            subclass(EditFolderRoute::class)
            subclass(AuthMenuInnerNavigationRoute::class)
            subclass(AuthMenuRoute::class)
            subclass(AuthRegisterRoute::class)
            subclass(AuthResetPasswordRoute::class)
            subclass(EmailConfirmRoute::class)
            subclass(ChooseWorkspaceRoute::class)
            subclass(DesktopAuthRoute::class)
        }
    }
}
