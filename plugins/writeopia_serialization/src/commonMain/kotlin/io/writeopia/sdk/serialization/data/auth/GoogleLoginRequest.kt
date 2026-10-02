package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

/**
 * Request body for `POST /api/auth/login/google` (and its `/web` cookie variant).
 *
 * Clients send either a Google ID token (Android Credential Manager, GoogleSignIn-iOS) or an
 * OAuth authorization code (desktop loopback flow, web GIS code client). When a code is sent,
 * [redirectUri] and [clientId] are required so the backend can exchange it with Google;
 * [codeVerifier] is required for PKCE flows (desktop) and absent for the web popup flow.
 */
@Serializable
data class GoogleLoginRequest(
    val idToken: String? = null,
    val code: String? = null,
    val codeVerifier: String? = null,
    val redirectUri: String? = null,
    val clientId: String? = null,
)
