package io.writeopia.auth.core.utils

import io.writeopia.app.sql.Writeopia_user_entity
import io.writeopia.sdk.models.user.WriteopiaUser

fun Writeopia_user_entity.toModel(): WriteopiaUser {
    return WriteopiaUser(
        id = this.id,
        email = this.email,
        password = this.password,
        name = this.name,
    )
}
