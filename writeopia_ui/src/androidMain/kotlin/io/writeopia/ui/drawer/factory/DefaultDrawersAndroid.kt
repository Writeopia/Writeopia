package io.writeopia.ui.drawer.factory

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.writeopia.ui.drawer.content.ImageDrawer
import io.writeopia.sdk.drawer.content.VideoDrawer
import io.writeopia.ui.drawer.content.defaultImageShape
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.drawer.content.RowGroupDrawer
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawConfig

object DefaultDrawersAndroid : DrawersFactory {

    @Composable
    override fun create(
        manager: WriteopiaStateManager,
        defaultBorder: Shape,
        editable: Boolean,
        groupsBackgroundColor: Color,
        onHeaderClick: () -> Unit,
    ): Map<Int, StoryStepDrawer> {
        val imageDrawer = ImageDrawer(
            containerModifier = Modifier::defaultImageShape,
            mergeRequest = manager::mergeRequest
        )

        val imageDrawerInGroup = ImageDrawer(
            containerModifier = Modifier::defaultImageShape,
            mergeRequest = manager::mergeRequest
        )

        val commonDrawers = CommonDrawers.create(
            manager,
            marginAtBottom = 500.dp,
            defaultBorder,
            editable,
            groupsBackgroundColor,
            onHeaderClick,
            lineBreakByContent = true,
            drawConfig = DrawConfig(
                textDrawerStartPadding = 2,
                textVerticalPadding = 4,
                codeBlockStartPadding = 0,
                checkBoxStartPadding = 0,
                checkBoxEndPadding = 0,
                checkBoxItemVerticalPadding = 0,
                listItemStartPadding = 8
            ),
            isDesktop = false,
            eventListener = KeyEventListenerFactory.android(
                manager,
                isEmptyErase = DefaultDrawersAndroid::emptyErase
            )
        )

        return mapOf(
            StoryTypes.VIDEO.type.number to VideoDrawer(),
            StoryTypes.IMAGE.type.number to imageDrawer,
            StoryTypes.GROUP_IMAGE.type.number to RowGroupDrawer(imageDrawerInGroup)
        ) + commonDrawers
    }

    private fun emptyErase(
        keyEvent: androidx.compose.ui.input.key.KeyEvent,
        input: TextFieldValue
    ): Boolean =
        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DEL && input.selection.start == 0
}
