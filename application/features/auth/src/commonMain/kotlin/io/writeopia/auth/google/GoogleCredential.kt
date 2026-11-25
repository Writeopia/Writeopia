package io.writeopia.auth.google

import io.writeopia.sdk.serialization.data.auth.GoogleLoginRequest

/** What a platform sign-in flow hands back, ready to be sent to the backend. */
sealed interface GoogleCredential {
    /** Android Credential Manager / native SDKs return a verified ID token directly. */
    data class IdToken(val idToken: String) : GoogleCredential

    /**
     * Browser based flows (desktop loopback, web popup) return an authorization code that the
     * backend exchanges with Google. [codeVerifier] is present for PKCE flows only.
     */
    data class AuthCode(
        val code: String,
        val codeVerifier: String?,
        val redirectUri: String,
        val clientId: String,
    ) : GoogleCredential
}

fun GoogleCredential.toRequest(): GoogleLoginRequest = when (this) {
    is GoogleCredential.IdToken -> GoogleLoginRequest(idToken = idToken)
    is GoogleCredential.AuthCode -> GoogleLoginRequest(
        code = code,
        codeVerifier = codeVerifier,
        redirectUri = redirectUri,
        clientId = clientId,
    )
}
