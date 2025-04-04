package io.writeopia.sdk.models.extensions

import io.writeopia.sdk.models.CREATED_AT
import io.writeopia.sdk.models.LAST_UPDATED_AT
import io.writeopia.sdk.models.TITLE
import io.writeopia.sdk.models.sorting.OrderBy

fun String.toEntityField(): String =
    when (this) {
        OrderBy.CREATE.type -> CREATED_AT
        OrderBy.UPDATE.type -> LAST_UPDATED_AT
        OrderBy.NAME.type -> TITLE
        else -> throw IllegalArgumentException("This type of order doesn't exists: $this")
    }
