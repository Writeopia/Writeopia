package io.writeopia.auth.google

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher? {
    if (!GoogleOAuthConfig.isConfigured) return null
    val activity = LocalActivity.current ?: return null
    return remember(activity) { AndroidGoogleSignInLauncher(activity) }
}

/**
 * Credential Manager based sign-in. The "Sign in with Google" option shows the full account
 * picker on a button tap. The web client ID is used as the server client ID so the returned
 * ID token has the web client as audience, which is what the backend verifies.
 */
class AndroidGoogleSignInLauncher(
    private val activity: Activity,
    private val serverClientId: String = GoogleOAuthConfig.WEB_CLIENT_ID,
) : GoogleSignInLauncher {

    override suspend fun signIn(): GoogleSignInResult {
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val credential = CredentialManager.create(activity)
                .getCredential(activity, request)
                .credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                GoogleSignInResult.Success(GoogleCredential.IdToken(idToken))
            } else {
                GoogleSignInResult.Failure(
                    IllegalStateException("Unexpected credential type: ${credential.type}")
                )
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failure(e)
        }
    }
}
