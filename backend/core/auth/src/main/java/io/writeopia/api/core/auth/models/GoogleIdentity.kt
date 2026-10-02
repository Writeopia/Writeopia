package io.writeopia.api.core.auth.models

import io.writeopia.api.core.auth.service.TokenPair

/** Claims extracted from a verified Google ID token. */
data class GoogleIdentity(
    /** Google's stable user id (`sub` claim) - the only thing safe to key an identity on. */
    val subject: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
    /** The `aud` claim: which of our OAuth clients the token was minted for. */
    val audience: String,
)

/** Thrown when an ID token can't be verified or an authorization code can't be exchanged. */
class GoogleTokenException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed class GoogleLoginResult {
    data class Success(
        val user: WriteopiaBeUser,
        val tokenPair: TokenPair,
        /** True when this login created the account (first sign-in with Google). */
        val created: Boolean,
    ) : GoogleLoginResult()

    data object DeletionPending : GoogleLoginResult()

    data class InvalidToken(val reason: String) : GoogleLoginResult()

    data class BadRequest(val reason: String) : GoogleLoginResult()
}
