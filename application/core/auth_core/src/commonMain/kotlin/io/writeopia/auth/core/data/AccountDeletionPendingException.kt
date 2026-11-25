package io.writeopia.auth.core.data

import io.writeopia.sdk.serialization.data.auth.GoogleSignUpRequiredResponse

/**
 * Thrown by [AuthApi.login]/[AuthApi.loginWeb] when the backend reports the account
 * is currently being deleted (HTTP 403), so callers can tell this apart from a
 * generic login failure and route the user to the account-deletion screen instead.
 */
class AccountDeletionPendingException : Exception("Account is being deleted")

/**
 * The Google account has no Writeopia account yet: the user picks a username and agrees to the
 * Terms, then the sign-in is sent again with [details]'s ID token.
 */
class GoogleSignUpRequiredException(
    val details: GoogleSignUpRequiredResponse,
) : Exception("Google sign-up required")

/** The username chosen for a new account is taken. */
class UsernameTakenException : Exception("Username taken")
