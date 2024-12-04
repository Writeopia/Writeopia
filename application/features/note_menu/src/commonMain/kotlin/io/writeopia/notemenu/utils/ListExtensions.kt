package io.writeopia.notemenu.utils

import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.persistence.core.sorting.OrderBy

fun List<MenuItem>.sortedWithOrderBy(orderBy: OrderBy): List<MenuItem> =
    when (orderBy) {
        OrderBy.CREATE -> this.sortedByDescending { menuItem -> menuItem.createdAt }
        OrderBy.UPDATE -> this.sortedByDescending { menuItem -> menuItem.lastUpdatedAt }
        OrderBy.NAME -> this.sortedBy { menuItem -> menuItem.title.lowercase() }
    }
