package io.writeopia.ui.drawer.factory

import android.view.KeyEvent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.drawer.content.VideoDrawer
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.icons.WrSdkIcons
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
        drawConfig: DrawConfig,
        fontFamily: FontFamily?,
        generateSection: (Int) -> Unit,
        receiveExternalFile: (List<ExternalFile>, Int) -> Unit,
        onDocumentLinkClick: (String) -> Unit,
    ): Map<Int, StoryStepDrawer> {
        val commonDrawers = CommonDrawers.create(
            manager,
            marginAtBottom = 500.dp,
            defaultBorder,
            editable,
            onHeaderClick,
            lineBreakByContent = true,
            isDesktop = false,
            drawConfig = drawConfig,
            eventListener = KeyEventListenerFactory.android(
                manager,
                isEmptyErase = DefaultDrawersAndroid::emptyErase
            ),
            fontFamily = fontFamily,
            headerEndContent = { storyStep, drawInfo, _ ->
                val isTitle = storyStep.tags.any { it.tag.isTitle() }
                val isCollapsed by lazy { storyStep.tags.any { it.tag == Tag.COLLAPSED } }
                if (isTitle) {
                    val iconTintOnHover = MaterialTheme.colorScheme.onBackground

                    Icon(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .combinedClickable(
                                onClick = { manager.toggleCollapseItem(drawInfo.position) },
                                onLongClick = { manager.onSectionSelected(drawInfo.position) }
                            )
                            .size(24.dp)
                            .padding(4.dp),
                        imageVector = if (isCollapsed) {
                            WrSdkIcons.smallArrowUp
                        } else {
                            WrSdkIcons.smallArrowDown
                        },
                        contentDescription = "Small arrow right",
                        tint = iconTintOnHover
                    )
                }
            },
            onDocumentLinkClick = onDocumentLinkClick,
        )

        return mapOf(
            StoryTypes.VIDEO.type.number to VideoDrawer(),
        ) + commonDrawers
    }

    private fun emptyErase(
        keyEvent: androidx.compose.ui.input.key.KeyEvent,
        input: TextFieldValue
    ): Boolean =
        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DEL && input.selection.start == 0
}
