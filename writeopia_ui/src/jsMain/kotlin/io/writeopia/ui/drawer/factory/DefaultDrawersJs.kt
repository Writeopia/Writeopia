package io.writeopia.ui.drawer.factory

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.story.Tag
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.icons.WrSdkIcons
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawConfig

object DefaultDrawersJs : DrawersFactory {

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun create(
        manager: WriteopiaStateManager,
        editable: Boolean,
        aiExplanation: String,
        isDarkTheme: Boolean,
        onHeaderClick: () -> Unit,
        drawConfig: DrawConfig,
        fontFamily: FontFamily?,
        generateSection: (Int) -> Unit,
        receiveExternalFile: (List<ExternalFile>, Int) -> Unit,
        onDocumentLinkClick: (String) -> Unit,
        equationToImageUrl: String?
    ): Map<Int, StoryStepDrawer> =
        CommonDrawers.create(
            manager,
            30.dp,
            aiExplanation,
            editable,
            onHeaderClick,
            dragIconWidth = 16.dp,
            lineBreakByContent = true,
            isDesktop = true,
            isDarkTheme = isDarkTheme,
            drawConfig = drawConfig,
            eventListener = KeyEventListenerFactory.desktop(manager),
            fontFamily = fontFamily,
            headerEndContent = { storyStep, drawInfo, isHovered ->
                val isTitle = storyStep.tags.any { it.tag.isTitle() }
                val isCollapsed by lazy { storyStep.tags.any { it.tag == Tag.COLLAPSED } }
                if (isTitle && isHovered || isCollapsed) {
                    var active by remember { mutableStateOf(false) }
                    val iconTintOnHover = MaterialTheme.colorScheme.onBackground
                    val iconTint = Color(0xFFAAAAAA)

                    val tintColor by derivedStateOf {
                        if (active) iconTintOnHover else iconTint
                    }

                    Icon(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .combinedClickable(
                                onClick = { manager.toggleCollapseItem(drawInfo.position) },
                                onLongClick = { manager.onSectionSelected(drawInfo.position) }
                            )
                            .onPointerEvent(PointerEventType.Enter) { active = true }
                            .onPointerEvent(PointerEventType.Exit) { active = false }
                            .size(24.dp)
                            .padding(4.dp),
                        imageVector = if (isCollapsed) {
                            WrSdkIcons.smallArrowUp
                        } else {
                            WrSdkIcons.smallArrowDown
                        },
                        contentDescription = "Small arrow right",
                        tint = tintColor
                    )
                }
            },
            onDocumentLinkClick = onDocumentLinkClick
        )
}
