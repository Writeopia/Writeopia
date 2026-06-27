@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.repository

import io.writeopia.api.core.auth.models.WriteopiaBeUser
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import java.util.UUID
import kotlin.time.ExperimentalTime

fun WriteopiaDbBackend.getUserByEmail(email: String): WriteopiaBeUser? =
    this.userEntityQueries
        .selectUserByEmail(email)
        .executeAsOneOrNull()
        ?.let { userEntity ->
            WriteopiaBeUser(
                id = userEntity.id,
                email = userEntity.email,
                password = userEntity.password,
                name = userEntity.name,
                salt = userEntity.salt,
                enabled = userEntity.enabled,
                confirmationCode = userEntity.confirmation_code,
                confirmationCodeExpiry = userEntity.confirmation_code_expiry
            )
        }

fun WriteopiaDbBackend.getEnabledUserByEmail(email: String): WriteopiaBeUser? =
    this.userEntityQueries
        .selectEnabledUserByEmail(email)
        .executeAsOneOrNull()
        ?.let { userEntity ->
            WriteopiaBeUser(
                id = userEntity.id,
                email = userEntity.email,
                password = userEntity.password,
                name = userEntity.name,
                salt = userEntity.salt,
                enabled = userEntity.enabled,
                confirmationCode = userEntity.confirmation_code,
                confirmationCodeExpiry = userEntity.confirmation_code_expiry
            )
        }

fun WriteopiaDbBackend.getUserById(id: String): WriteopiaBeUser? =
    this.userEntityQueries
        .selectUserById(id)
        .executeAsOneOrNull()
        ?.let { userEntity ->
            WriteopiaBeUser(
                id = userEntity.id,
                email = userEntity.email,
                password = userEntity.password,
                name = userEntity.name,
                salt = userEntity.salt,
                enabled = userEntity.enabled,
                confirmationCode = userEntity.confirmation_code,
                confirmationCodeExpiry = userEntity.confirmation_code_expiry
            )
        }

fun WriteopiaDbBackend.insertUser(
    id: String = UUID.randomUUID().toString(),
    name: String,
    email: String,
    password: String,
    salt: String,
    enabled: Boolean,
    confirmationCode: String? = null,
    confirmationCodeExpiry: Long? = null,
) {
    this.userEntityQueries.insertUser(
        id = id,
        created_at = Clock.System.now().toEpochMilliseconds(),
        email = email,
        password = password,
        salt = salt,
        name = name,
        enabled = enabled,
        confirmation_code = confirmationCode,
        confirmation_code_expiry = confirmationCodeExpiry,
    )
}

fun WriteopiaDbBackend.insertUser(
    user: WriteopiaBeUser
) {
    insertUser(
        id = user.id,
        name = user.name,
        email = user.email,
        password = user.password,
        salt = user.salt,
        enabled = user.enabled,
        confirmationCode = user.confirmationCode,
        confirmationCodeExpiry = user.confirmationCodeExpiry,
    )
}

fun WriteopiaDbBackend.deleteUserById(id: String) {
    this.userEntityQueries.deleteUser(id)
}

fun WriteopiaDbBackend.deleteUserByEmail(email: String) {
    this.userEntityQueries.deleteUserByEmail(email)
}

fun WriteopiaDbBackend.enableUserByEmail(email: String) {
    this.userEntityQueries.enableUserByEmail(email)
}

fun WriteopiaDbBackend.disableUserByEmail(email: String) {
    this.userEntityQueries.disableUserByEmail(email)
}

fun WriteopiaDbBackend.updateConfirmationCode(email: String, code: String, expiry: Long) {
    this.userEntityQueries.updateConfirmationCode(code, expiry, email)
}

fun WriteopiaDbBackend.clearConfirmationCode(email: String) {
    this.userEntityQueries.clearConfirmationCode(email)
}

data class ConfirmationCodeData(val code: String?, val expiry: Long?)

fun WriteopiaDbBackend.getConfirmationCode(email: String): ConfirmationCodeData? =
    this.userEntityQueries
        .selectConfirmationCodeByEmail(email)
        .executeAsOneOrNull()
        ?.let { result ->
            ConfirmationCodeData(
                code = result.confirmation_code,
                expiry = result.confirmation_code_expiry
            )
        }

fun WriteopiaDbBackend.isCodeValid(email: String, code: String): Boolean {
    val data = getConfirmationCode(email) ?: return false
    val currentTime = Clock.System.now().toEpochMilliseconds()
    return data.code == code && (data.expiry ?: 0) > currentTime
}
