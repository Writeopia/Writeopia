package io.writeopia.sdk.models.sorting

enum class OrderBy(val type: String) {
    CREATE("created_at"),
    UPDATE("last_updated_at"),
    NAME(type = "title");

    companion object {
        fun fromString(stringValue: String) = entries
            .firstOrNull { orderBy -> orderBy.type == stringValue }
            ?: throw IllegalArgumentException("Not found: $stringValue")
    }
}
