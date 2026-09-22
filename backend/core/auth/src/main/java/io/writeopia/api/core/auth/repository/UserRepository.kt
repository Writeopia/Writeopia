@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.repository

import io.writeopia.api.core.auth.models.UserStatus
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
                username = userEntity.username,
                password = userEntity.password,
                name = userEntity.name,
                salt = userEntity.salt,
                confirmationCode = userEntity.confirmation_code,
                confirmationCodeExpiry = userEntity.confirmation_code_expiry,
                status = UserStatus.fromString(userEntity.status)
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
                username = userEntity.username,
                password = userEntity.password,
                name = userEntity.name,
                salt = userEntity.salt,
                confirmationCode = userEntity.confirmation_code,
                confirmationCodeExpiry = userEntity.confirmation_code_expiry,
                status = UserStatus.fromString(userEntity.status)
            )
        }

fun WriteopiaDbBackend.insertUser(
    id: String = UUID.randomUUID().toString(),
    name: String,
    username: String,
    email: String,
    password: String,
    salt: String,
    status: UserStatus,
    confirmationCode: String? = null,
    confirmationCodeExpiry: Long? = null,
    accountType: String = "FREE",
) {
    this.userEntityQueries.insertUser(
        id = id,
        created_at = Clock.System.now().toEpochMilliseconds(),
        name = name,
        username = username,
        email = email,
        password = password,
        salt = salt,
        confirmation_code = confirmationCode,
        confirmation_code_expiry = confirmationCodeExpiry,
        account_type = accountType,
        status = status.value,
    )
}

fun WriteopiaDbBackend.insertUser(
    user: WriteopiaBeUser
) {
    insertUser(
        id = user.id,
        name = user.name,
        username = user.username,
        email = user.email,
        password = user.password,
        salt = user.salt,
        status = user.status,
        confirmationCode = user.confirmationCode,
        confirmationCodeExpiry = user.confirmationCodeExpiry,
    )
}

fun WriteopiaDbBackend.userExistsByUsernameOrEmail(username: String, email: String): Boolean =
    this.userEntityQueries
        .userExistsByUsernameOrEmail(username, email) 
        .executeAsOne()

fun WriteopiaDbBackend.updatePassword(id: String, password: String, salt: String) {
    this.userEntityQueries.updatePassword(password, salt, id)
}

/** Idempotent: no-op if the user is already DELETION_PENDING. Returns true if this call changed it. */
suspend fun WriteopiaDbBackend.setUserStatusPendingDeletion(id: String): Boolean =
    this.userEntityQueries.setStatusPendingDeletion(id).await() > 0

fun WriteopiaDbBackend.getUserStatus(id: String): UserStatus? =
    this.userEntityQueries.selectStatusById(id).executeAsOneOrNull()?.let(UserStatus::fromString)

suspend fun WriteopiaDbBackend.deleteUserById(id: String): Long {
    return this.userEntityQueries.deleteUser(id).await()
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

data class UserSearchResult(val id: String, val name: String, val email: String)

fun WriteopiaDbBackend.searchUsersByEmail(
    emailQuery: String,
    limit: Long,
    offset: Long
): List<UserSearchResult> =
    this.userEntityQueries
        .searchUsersByEmail(emailQuery, limit, offset)
        .executeAsList()
        .map { result ->
            UserSearchResult(
                id = result.id,
                name = result.name,
                email = result.email
            )
        }
