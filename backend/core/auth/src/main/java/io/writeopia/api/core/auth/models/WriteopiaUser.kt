package io.writeopia.api.core.auth.models

import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.serialization.data.WriteopiaUserApi

/**
 * Lifecycle status of a user account, backed by the `status` column on `user_entity`.
 * Replaces the old standalone `enabled` boolean (which only ever meant "email confirmed") -
 * confirmation and the account-deletion saga are really two states of the same lifecycle, not
 * two independent flags.
 */
enum class UserStatus(val value: String) {
    /** Registered, but hasn't clicked/entered the email confirmation code yet. Can't log in. */
    EMAIL_CONFIRMATION_PENDING("EMAIL_CONFIRMATION_PENDING"),

    /** Normal, fully usable account. */
    ACTIVE("ACTIVE"),

    /** Account-deletion saga in flight (see AccountDeletionService) - login is rejected. */
    DELETION_PENDING("DELETION_PENDING");

    companion object {
        /** Defaults to the most restrictive state on an unrecognized value, fail-closed. */
        fun fromString(value: String): UserStatus =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: EMAIL_CONFIRMATION_PENDING
    }
}

data class WriteopiaBeUser(
    val id: String,
    val email: String,
    val username: String,
    val name: String,
    val password: String,
    val salt: String,
    val status: UserStatus,
    val tier: Tier = Tier.FREE,
    val confirmationCode: String? = null,
    val confirmationCodeExpiry: Long? = null,
) {
    companion object {
        const val DISCONNECTED = "disconnected_user"

        fun disconnectedUser(): WriteopiaBeUser =
            WriteopiaBeUser(
                id = "disconnected_user",
                email = "",
                username = "",
                name = "",
                password = "",
                salt = "",
                status = UserStatus.EMAIL_CONFIRMATION_PENDING,
            )
    }
}


fun WriteopiaBeUser.toApi() =
    WriteopiaUserApi(
        id = id,
        email = email,
        name = name
    )

fun WriteopiaUserApi.toModel() =
    WriteopiaUser(
        id = id,
        email = email,
        name = name,
    )
