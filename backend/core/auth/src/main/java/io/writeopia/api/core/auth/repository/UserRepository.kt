package io.writeopia.api.core.auth.repository

import io.writeopia.api.core.auth.model.WriteopiaUser
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

fun WriteopiaDbBackend.insertUser(name: String, email: String, password: String) {
    this.userEntityQueries.insertUser(
        id = UUID.randomUUID().toString(),
        created_at = Clock.System.now().toEpochMilliseconds(),
        email = email,
        password = password,
        name = name,
        enabled = false,
    )
}
