package io.writeopia.api.core.auth.repository

import io.writeopia.api.core.auth.model.WriteopiaUser
import io.writeopia.sql.WriteopiaDbBackend

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
