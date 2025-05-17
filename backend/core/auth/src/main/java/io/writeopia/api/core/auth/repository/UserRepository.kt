package io.writeopia.api.core.auth.repository

import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.datetime.Clock
import java.util.UUID

fun WriteopiaDbBackend.getUser(email: String, password: String): WriteopiaUser? =
    this.userEntityQueries
        .selectUser(email, password)
        .executeAsOneOrNull()
        ?.let { userEntity ->
            WriteopiaUser(
                id = userEntity.id,
                email = userEntity.email,
                password = userEntity.password,
            )
        }

fun WriteopiaDbBackend.getUserByEmail(email: String): WriteopiaUser? =
    this.userEntityQueries
        .selectUserByEmail(email)
        .executeAsOneOrNull()
        ?.let { userEntity ->
            WriteopiaUser(
                id = userEntity.id,
                email = userEntity.email,
                password = userEntity.password,
            )
        }

fun WriteopiaDbBackend.insertUser(
    id: String = UUID.randomUUID().toString(),
    name: String,
    email: String,
    password: String
) {
    this.userEntityQueries.insertUser(
        id = id,
        created_at = Clock.System.now().toEpochMilliseconds(),
        email = email,
        password = password,
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
