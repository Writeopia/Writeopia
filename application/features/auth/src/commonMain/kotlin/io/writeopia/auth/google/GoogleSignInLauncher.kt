package io.writeopia.auth.google

import androidx.compose.runtime.Composable

sealed interface GoogleSignInResult {
    data class Success(val credential: GoogleCredential) : GoogleSignInResult

    /** The user dismissed the Google UI; nothing to report. */
    data object Cancelled : GoogleSignInResult

    data class Failure(val error: Throwable) : GoogleSignInResult
}

/** Runs the platform specific Google UI and returns a credential for the backend. */
interface GoogleSignInLauncher {
    suspend fun signIn(): GoogleSignInResult
}

/**
 * Provides the launcher for the current platform, or null when Google sign-in is not available
 * here (platform without an implementation, or client IDs not configured). Callers hide the
 * button when this returns null.
 */
@Composable
expect fun rememberGoogleSignInLauncher(): GoogleSignInLauncher?
