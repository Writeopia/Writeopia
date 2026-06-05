package io.writeopia.model

import androidx.compose.ui.graphics.Color

enum class AccentColor(val id: String, val lightColor: Color, val darkColor: Color) {
    PURPLE("purple", Color(0xFFB7409A), Color(0xFFE987D0)),
    BLUE("blue", Color(0xFF2196F3), Color(0xFF64B5F6)),
    GREEN("green", Color(0xFF4CAF50), Color(0xFF81C784)),
    ORANGE("orange", Color(0xFFFF9800), Color(0xFFFFB74D)),
    DYNAMIC("dynamic", Color(0xFFB7409A), Color(0xFFE987D0));

    companion object {
        fun fromId(id: String?): AccentColor = entries.find { it.id == id } ?: PURPLE
    }
}
