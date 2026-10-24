package io.writeopia.editor.features.editor.ui.desktop.edit.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun LockButton(
    isEditableState: StateFlow<Boolean>,
    setEditable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CommonButton(
        modifier = modifier,
        icon = WrIcons.lock,
        iconDescription = WrStrings.lockDocument(),
        text = WrStrings.lockDocument(),
        isEnabledState = isEditableState,
        clickListener = setEditable
    )
}

@Composable
fun MoveToButton(clickListener: () -> Unit, modifier: Modifier = Modifier) {
    CommonButton(
        modifier = modifier,
        icon = WrIcons.move,
        iconDescription = WrStrings.moveTo(),
        text = WrStrings.moveTo(),
        clickListener = clickListener
    )
}

@Composable
fun FavoriteButton(isFavorite: StateFlow<Boolean>, clickListener: () -> Unit, modifier: Modifier = Modifier) {
    CommonButton(
        modifier = modifier,
        icon = WrIcons.favorites,
        iconDescription = WrStrings.favorite(),
        text = WrStrings.favorite(),
        clickListener = clickListener,
        isEnabledState = isFavorite
    )
}

@Composable
fun DeleteButton(clickListener: () -> Unit, modifier: Modifier = Modifier) {
    CommonButton(
        modifier = modifier,
        icon = WrIcons.delete,
        iconDescription = WrStrings.delete(),
        text = WrStrings.delete(),
        clickListener = clickListener
    )
}

/** Opens the icons for the title. Highlighted while they are open. */
@Composable
fun DocumentIconButton(isOpen: Boolean, clickListener: () -> Unit, modifier: Modifier = Modifier) {
    CommonButton(
        modifier = modifier,
        icon = WrIcons.documentIcon,
        iconDescription = WrStrings.documentIcon(),
        text = WrStrings.documentIcon(),
        defaultColor = if (isOpen) {
            WriteopiaTheme.colorScheme.highlight
        } else {
            WriteopiaTheme.colorScheme.defaultButton
        },
        clickListener = clickListener
    )
}
