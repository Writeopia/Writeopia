package io.writeopia.editor.configuration.ui

import androidx.compose.material3.MaterialTheme
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.model.DrawConfig

actual object DrawConfigFactory {
    actual fun getDrawConfig(): DrawConfig =
        DrawConfig(
            selectedColor = { WriteopiaTheme.colorScheme.selectedBg },
            selectedBorderColor = { MaterialTheme.colorScheme.primary },
            dividerColor = { WriteopiaTheme.colorScheme.dividerColor }
        )
}
