package io.writeopia.sdk.models.workspace

enum class Role(val value: String) {
    ADMIN("ADMIN"),
    EDITOR("EDITOR");

    companion object {
        fun fromString(value: String): Role =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: EDITOR
    }
}
