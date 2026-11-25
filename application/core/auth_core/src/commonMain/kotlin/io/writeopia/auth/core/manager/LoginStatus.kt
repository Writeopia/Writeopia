package io.writeopia.auth.core.manager

enum class LoginStatus {
    OFFLINE_NOT_CHOSEN,
    OFFLINE_CHOSEN,
    CHOOSE_WORKSPACE,
    ONLINE,
    EMAIL_NOT_CONFIRMED,

    /** Signed in without a workspace yet, and not known to be premium: the paywall decides. */
    SUBSCRIPTION_REQUIRED
}
