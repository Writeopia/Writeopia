package io.writeopia.sqldelight.extensions

import io.writeopia.app.sql.FolderEntity
import io.writeopia.common.utils.extensions.toBoolean
import io.writeopia.models.Folder
import io.writeopia.sdk.models.document.MenuItem
import kotlinx.datetime.Instant

fun FolderEntity.toModel(count: Long) =
    Folder(
        id = this.id,
        parentId = this.parent_id,
        title = title,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        lastUpdatedAt = Instant.fromEpochMilliseconds(last_updated_at),
        userId = user_id,
        itemCount = count,
        favorite = favorite.toBoolean(),
        icon = if (this.icon != null && this.icon_tint != null) MenuItem.Icon(
            this.icon,
            this.icon_tint.toInt()
        ) else null
    )
