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
    /** The `hd` claim: the Google Workspace domain of the account, null for consumer accounts. */
    val hostedDomain: String? = null,
) {
    /**
     * Whether Google owns the truth about [email]: a Gmail address, or an address of the account's
     * own Workspace domain. Any other address was verified once, when the Google account was made,
     * and may belong to someone else by now, so it can't stand for an existing Writeopia account.
     */
    val isEmailAuthoritative: Boolean
        get() {
            val domain = email.substringAfterLast('@').lowercase()
            return domain == "gmail.com" ||
                domain == "googlemail.com" ||
                (hostedDomain != null && hostedDomain.equals(domain, ignoreCase = true))
        }
}

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

    /** No account yet: the user must pick a username and agree to the Terms first. */
    data class SignUpRequired(
        val idToken: String,
        val email: String,
        val name: String,
        val suggestedUsername: String,
    ) : GoogleLoginResult()

    data object UsernameTaken : GoogleLoginResult()

    /**
     * A password account has this email, but Google is not authoritative for it: the user signs
     * in with the password instead, and the Google identity is not linked.
     */
    data object EmailNotLinkable : GoogleLoginResult()

    data class InvalidToken(val reason: String) : GoogleLoginResult()

    data class BadRequest(val reason: String) : GoogleLoginResult()
}
