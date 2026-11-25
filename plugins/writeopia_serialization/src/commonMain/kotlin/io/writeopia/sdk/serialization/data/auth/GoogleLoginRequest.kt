package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

/**
 * Request body for `POST /api/auth/login/google` (and its `/web` cookie variant).
 *
 * Clients send either a Google ID token (Android Credential Manager, GoogleSignIn-iOS) or an
 * OAuth authorization code (desktop loopback flow, web GIS code client). When a code is sent,
 * [redirectUri] and [clientId] are required so the backend can exchange it with Google;
 * [codeVerifier] is required for PKCE flows (desktop) and absent for the web popup flow.
 *
 * The first sign-in of a Google account with no Writeopia account answers 422 with a
 * [GoogleSignUpRequiredResponse]; the client then asks the user for a [username] and the
 * agreement to the Terms and Privacy Policy, and sends the request again with the ID token it
 * received, [username] and [acceptedLegalVersion].
 */
@Serializable
data class GoogleLoginRequest(
    val idToken: String? = null,
    val code: String? = null,
    val codeVerifier: String? = null,
    val redirectUri: String? = null,
    val clientId: String? = null,
    val username: String? = null,
    val acceptedLegalVersion: String? = null,
)

/**
 * Body of the 422 answer to a Google sign-in with no Writeopia account yet: the account is made
 * once the user picks a username and agrees to the Terms. [idToken] is the verified Google ID
 * token, to send back (a code can only be exchanged once); it expires within the hour.
 */
@Serializable
data class GoogleSignUpRequiredResponse(
    val idToken: String,
    val email: String,
    val name: String,
    val suggestedUsername: String,
)
