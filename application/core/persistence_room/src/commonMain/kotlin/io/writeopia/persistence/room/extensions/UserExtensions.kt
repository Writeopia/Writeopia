package io.writeopia.persistence.room.extensions

import io.writeopia.persistence.room.data.entities.UserEntity
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser

fun UserEntity.toModel(): Pair<WriteopiaUser, Boolean> =
    WriteopiaUser(
        id = id,
        name = name,
        email = email,
        tier = Tier.valueOf(tier),
    ) to selected

fun WriteopiaUser.toEntity(selected: Boolean): UserEntity =
    UserEntity(
        id = id,
        name = name,
        email = email,
        tier = tier.name,
        selected = selected
    )
