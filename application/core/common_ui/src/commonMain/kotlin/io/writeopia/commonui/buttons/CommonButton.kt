package io.writeopia.commonui.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AccentButton(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconDescription: String? = null,
    text: String,
    defaultColor: Color = WriteopiaTheme.colorScheme.defaultButton,
    selectedColor: Color = WriteopiaTheme.colorScheme.highlight,
    isEnabledState: StateFlow<Boolean> = MutableStateFlow(true),
    clickListener: () -> Unit,
) {
    CommonButton(
        modifier = modifier,
        icon = icon,
        iconDescription = iconDescription,
        text = text,
        defaultColor = WriteopiaTheme.colorScheme.cardBg,
        selectedColor = selectedColor,
        isEnabledState = isEnabledState,
        clickListener = clickListener
    )
}

@Composable
fun CommonButton(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconDescription: String? = null,
    text: String,
    defaultColor: Color = WriteopiaTheme.colorScheme.defaultButton,
    selectedColor: Color = WriteopiaTheme.colorScheme.highlight,
    isEnabledState: StateFlow<Boolean> = MutableStateFlow(true),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    clickListener: () -> Unit,
) {
    val isEditable by isEnabledState.collectAsState()
    val lockButtonColor = if (isEditable) defaultColor else selectedColor

    val shape = MaterialTheme.shapes.medium

    Row(
        verticalAlignment = verticalAlignment,
        horizontalArrangement = horizontalArrangement,
        modifier = modifier
            .background(lockButtonColor, shape)
            .clip(shape)
            .clickable(onClick = clickListener)
            .padding(horizontal = 10.dp, vertical = verticalPaddingCommonButton().dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(14.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))
        }

        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CommonTextButton(
    modifier: Modifier = Modifier,
    text: String,
    defaultColor: Color = WriteopiaTheme.colorScheme.defaultButton,
    selectedColor: Color = WriteopiaTheme.colorScheme.highlight,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    isEnabledState: StateFlow<Boolean> = MutableStateFlow(true),
    clickListener: () -> Unit,
) {
    val isEditable by isEnabledState.collectAsState()
    val lockButtonColor = if (isEditable) defaultColor else selectedColor

    val shape = MaterialTheme.shapes.medium

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(lockButtonColor, shape)
            .clip(shape)
            .clickable(onClick = clickListener)
            .padding(horizontal = 10.dp, vertical = verticalPaddingCommonButton().dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
