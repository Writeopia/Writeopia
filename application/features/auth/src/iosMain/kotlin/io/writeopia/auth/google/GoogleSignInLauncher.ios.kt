package io.writeopia.auth.google

import androidx.compose.runtime.Composable

/** The native Swift app (iosAppNat) owns Google sign-in on iOS; the Compose iOS target hides the button. */
@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher? = null
