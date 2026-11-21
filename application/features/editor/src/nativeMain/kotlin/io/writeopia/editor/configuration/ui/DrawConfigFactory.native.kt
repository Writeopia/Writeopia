package io.writeopia.editor.configuration.ui

import androidx.compose.material3.MaterialTheme
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.model.DrawConfig

actual object DrawConfigFactory {
    actual fun getDrawConfig(): DrawConfig = DrawConfig(
        textDrawerStartPadding = 2,
        textVerticalPadding = 4,
        codeBlockStartPadding = 0,
        checkBoxStartPadding = 0,
        checkBoxEndPadding = 0,
        checkBoxItemVerticalPadding = 0,
        listItemStartPadding = 8,
        selectedColor = { WriteopiaTheme.colorScheme.selectedBg },
        selectedBorderColor = { MaterialTheme.colorScheme.primary },
        dividerColor = { WriteopiaTheme.colorScheme.dividerColor }
    )
}
