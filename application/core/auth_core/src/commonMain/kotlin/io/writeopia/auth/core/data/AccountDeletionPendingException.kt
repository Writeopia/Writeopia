package io.writeopia.auth.core.data

/**
 * Thrown by [AuthApi.login]/[AuthApi.loginWeb] when the backend reports the account
 * is currently being deleted (HTTP 403), so callers can tell this apart from a
 * generic login failure and route the user to the account-deletion screen instead.
 */
class AccountDeletionPendingException : Exception("Account is being deleted")
