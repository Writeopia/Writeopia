package io.writeopia.editor.features.editor.ui.desktop.edit.menu

import androidx.compose.runtime.Composable
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.resources.WrStrings
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
