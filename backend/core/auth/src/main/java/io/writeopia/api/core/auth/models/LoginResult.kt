package io.writeopia.api.core.auth.models

import io.writeopia.api.core.auth.service.TokenPair

/**
 * Result of validating login credentials, shared between the token-based and
 * cookie-based login routes. Callers decide how to turn [Success] into a response
 * (tokens in the body vs. HttpOnly cookies).
 */
sealed class LoginResult {
    data class Success(val user: WriteopiaBeUser, val tokenPair: TokenPair) : LoginResult()
    data class NotConfirmed(val user: WriteopiaBeUser) : LoginResult()
    object DeletionPending : LoginResult()
    object InvalidCredentials : LoginResult()
}
