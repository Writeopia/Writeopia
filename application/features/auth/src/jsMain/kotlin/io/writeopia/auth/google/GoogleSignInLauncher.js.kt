package io.writeopia.auth.google

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher? {
    if (!GoogleOAuthConfig.isConfigured) return null
    return remember { WebGoogleSignInLauncher() }
}

/**
 * Google Identity Services "code model" in popup mode. The `gsi/client` script is loaded by
 * index.html. The popup returns an authorization code that the backend exchanges with Google;
 * the redirect URI for that exchange is the page origin, as GIS documents for popup mode.
 *
 * [signIn] must be called synchronously from the click handler (no dispatch in between), or the
 * browser treats the popup as unsolicited and blocks it.
 */
class WebGoogleSignInLauncher(
    private val clientId: String = GoogleOAuthConfig.WEB_CLIENT_ID,
) : GoogleSignInLauncher {

    override suspend fun signIn(): GoogleSignInResult {
        if (!isGisAvailable()) {
            return GoogleSignInResult.Failure(IllegalStateException("Google Identity Services not loaded"))
        }

        return suspendCancellableCoroutine { continuation ->
            var resumed = false

            fun finish(result: GoogleSignInResult) {
                if (!resumed && continuation.isActive) {
                    resumed = true
                    continuation.resume(result)
                }
            }

            val onCode: (dynamic) -> Unit = { response ->
                val error = response.error as String?
                val code = response.code as String?
                when {
                    error == "access_denied" -> finish(GoogleSignInResult.Cancelled)
                    error != null -> finish(GoogleSignInResult.Failure(IllegalStateException("Google returned: $error")))
                    code.isNullOrBlank() -> finish(GoogleSignInResult.Failure(IllegalStateException("No authorization code received")))
                    else -> finish(
                        GoogleSignInResult.Success(
                            GoogleCredential.AuthCode(
                                code = code,
                                codeVerifier = null,
                                redirectUri = window.location.origin,
                                clientId = clientId,
                            )
                        )
                    )
                }
            }

            val onError: (dynamic) -> Unit = { error ->
                val type = error?.type as String?
                if (type == "popup_closed") {
                    finish(GoogleSignInResult.Cancelled)
                } else {
                    finish(GoogleSignInResult.Failure(IllegalStateException("Google sign-in failed: ${type ?: "unknown"}")))
                }
            }

            try {
                val client = initCodeClient(clientId, onCode, onError)
                client.requestCode()
            } catch (e: Throwable) {
                finish(GoogleSignInResult.Failure(e))
            }
        }
    }

    private fun isGisAvailable(): Boolean =
        js("typeof google !== 'undefined' && !!google.accounts && !!google.accounts.oauth2") as Boolean

    private fun initCodeClient(
        clientId: String,
        callback: (dynamic) -> Unit,
        errorCallback: (dynamic) -> Unit
    ): dynamic =
        js(
            """
            google.accounts.oauth2.initCodeClient({
                client_id: clientId,
                scope: 'openid email profile',
                ux_mode: 'popup',
                select_account: true,
                callback: callback,
                error_callback: errorCallback
            })
            """
        )
}
