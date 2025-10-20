package io.writeopia.app.mapping

import io.writeopia.app.dto.WorkspaceUserApi
import io.writeopia.models.user.WorkspaceUser

fun WorkspaceUserApi.toModel(): WorkspaceUser {
    return WorkspaceUser(
        id = id,
        email = email,
        name = name,
        role = role
    )
}

fun WorkspaceUser.toApi(): WorkspaceUserApi {
    return WorkspaceUserApi(
        id = id,
        email = email,
        name = name,
        role = role
    )
}
