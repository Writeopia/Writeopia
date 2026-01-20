package io.writeopia.ui.model

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

data class DrawConfig(
    val titleStyle: @Composable (FontFamily?) -> TextStyle = { font ->
        MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = font
        )
    },
    val titlePlaceHolderStyle: @Composable (FontFamily?) -> TextStyle = { font ->
        MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color.LightGray,
            fontFamily = font
        )
    },
    val textDrawerStartPadding: Int = 8,
    val textDrawerInnerStartPadding: Int = 4,
    val textVerticalPadding: Int = 0,
    val iaAnswerHorizontalPadding: Int = 12,
    val linkHorizontalPadding: Int = 12,
    val linkVerticalPadding: Int = 12,
    val codeBlockStartPadding: Int = 8,
    val codeBlockHorizontalInnerPadding: Int = 12,
    val codeBlockVerticalInnerPadding: Int = 8,
    val checkBoxStartPadding: Int = 16,
    val checkBoxEndPadding: Int = 8,
    val checkBoxItemVerticalPadding: Int = 8,
    val listItemStartPadding: Int = 16,
    val listItemEndPadding: Int = 8,
    val listItemItemVerticalPadding: Int = 8,
    val selectedColor: @Composable () -> Color = { MaterialTheme.colorScheme.primary },
    val selectedBorderColor: @Composable () -> Color = { MaterialTheme.colorScheme.primary },
    val dividerColor: @Composable () -> Color = { MaterialTheme.colorScheme.surfaceVariant },
)
