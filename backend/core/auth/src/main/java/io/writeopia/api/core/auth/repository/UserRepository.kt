package io.writeopia.api.core.auth.repository

import io.writeopia.api.core.auth.models.WriteopiaBeUser
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.datetime.Clock
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
                salt = userEntity.salt
            )
        }

fun WriteopiaDbBackend.insertUser(
    id: String = UUID.randomUUID().toString(),
    name: String,
    email: String,
    password: String,
    salt: String
) {
    this.userEntityQueries.insertUser(
        id = id,
        created_at = Clock.System.now().toEpochMilliseconds(),
        email = email,
        password = password,
        salt = salt,
        name = name,
        enabled = false,
    )
}

fun WriteopiaDbBackend.deleteUserById(id: String) {
    this.userEntityQueries.deleteUser(id)
}

fun WriteopiaDbBackend.deleteUserByEmail(email: String) {
    this.userEntityQueries.deleteUserByEmail(email)
}
