package io.writeopia.api.core.auth.repository

import io.writeopia.api.core.auth.models.WriteopiaBeUser
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import java.util.UUID

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
                enabled = userEntity.enabled
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
                enabled = userEntity.enabled
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
                enabled = userEntity.enabled
            )
        }

fun WriteopiaDbBackend.insertUser(
    id: String = UUID.randomUUID().toString(),
    name: String,
    email: String,
    password: String,
    salt: String,
    enabled: Boolean,
) {
    this.userEntityQueries.insertUser(
        id = id,
        created_at = Clock.System.now().toEpochMilliseconds(),
        email = email,
        password = password,
        salt = salt,
        name = name,
        enabled = enabled,
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
