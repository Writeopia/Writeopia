package io.writeopia.auth.google

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher? {
    if (!GoogleOAuthConfig.isDesktopConfigured) return null
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) { DesktopGoogleSignInLauncher(openUrl = uriHandler::openUri) }
}
