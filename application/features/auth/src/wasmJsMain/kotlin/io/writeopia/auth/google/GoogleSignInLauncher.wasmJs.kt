package io.writeopia.auth.google

import androidx.compose.runtime.Composable

/** The wasmJs entry point has no auth flow, so Google sign-in is not offered there. */
@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher? = null
