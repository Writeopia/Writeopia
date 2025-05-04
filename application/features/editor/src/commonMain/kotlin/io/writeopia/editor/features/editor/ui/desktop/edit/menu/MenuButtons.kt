package io.writeopia.editor.features.editor.ui.desktop.edit.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun LockButton(
    isEditableState: StateFlow<Boolean>,
    setEditable: () -> Unit,
) {
    CommonButton(
        icon = WrIcons.lock,
        iconDescription = WrStrings.lockDocument(),
        text = WrStrings.lockDocument(),
        isEnabledState = isEditableState,
        clickListener = setEditable
    )
}

@Composable
fun MoveToButton(clickListener: () -> Unit) {
    CommonButton(
        icon = WrIcons.move,
        iconDescription = WrStrings.moveTo(),
        text = WrStrings.moveTo(),
        clickListener = clickListener
    )
}

@Composable
fun MoveToHomeButton(clickListener: () -> Unit) {
    CommonButton(
        icon = WrIcons.move,
        iconDescription = WrStrings.moveToHome(),
        text = WrStrings.moveToHome(),
        clickListener = clickListener
    )
}

@Composable
fun FavoriteButton(isFavorite: StateFlow<Boolean>, clickListener: () -> Unit) {
    CommonButton(
        icon = WrIcons.favorites,
        iconDescription = WrStrings.favorite(),
        text = WrStrings.favorite(),
        clickListener = clickListener,
        isEnabledState = isFavorite
    )
}

@Composable
private fun CommonButton(
    icon: ImageVector,
    iconDescription: String,
    text: String,
    defaultColor: Color = WriteopiaTheme.colorScheme.defaultButton,
    selectedColor: Color = WriteopiaTheme.colorScheme.highlight,
    isEnabledState: StateFlow<Boolean> = MutableStateFlow(true),
    clickListener: () -> Unit,
) {
    val isEditable by isEnabledState.collectAsState()
    val lockButtonColor = if (isEditable) defaultColor else selectedColor

    val shape = MaterialTheme.shapes.medium

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(lockButtonColor, shape)
            .clip(shape)
            .clickable(onClick = clickListener)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(14.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
}
