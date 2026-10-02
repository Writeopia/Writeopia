@file:OptIn(ExperimentalTime::class)

package io.writeopia.editor.features.presentation.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.editor.configuration.ui.DrawConfigFactory
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.presentation.Slide
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.WriteopiaEditor
import io.writeopia.ui.drawer.factory.DrawersFactory
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Shows a presentation one slide at a time, like the Mac app and `PresentationScreen` of the
 * prototype: each slide drawn by the Writeopia editor, read only and centered, with the arrows
 * below to move between slides. The index is hoisted so the window can move with the keyboard.
 */
@Composable
fun PresentationViewer(
    presentation: Presentation,
    index: Int,
    onIndexChange: (Int) -> Unit,
    isDarkTheme: Boolean,
    drawersFactory: DrawersFactory,
    modifier: Modifier = Modifier
) {
    val slides = presentation.slides
    val count = slides.size

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (slides.isEmpty()) {
            Text(
                text = "This presentation has no slides",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            Crossfade(targetState = index.coerceIn(0, count - 1), modifier = Modifier.fillMaxSize()) { current ->
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.Center) {
                    SlideContent(
                        slide = slides[current],
                        slideId = "${presentation.id}-$current",
                        isDarkTheme = isDarkTheme,
                        drawersFactory = drawersFactory
                    )
                }
            }
        }

        if (count > 0) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tint = MaterialTheme.colorScheme.onBackground
                val iconSize = 40.dp

                Icon(
                    imageVector = WrIcons.circularArrowLeft,
                    contentDescription = "Previous slide",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = index > 0) { onIndexChange(index - 1) }
                        .padding(6.dp)
                        .size(iconSize),
                    tint = if (index > 0) tint else tint.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "${index + 1} / $count",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    imageVector = WrIcons.circularArrowRight,
                    contentDescription = "Next slide",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = index < count - 1) { onIndexChange(index + 1) }
                        .padding(6.dp)
                        .size(iconSize),
                    tint = if (index < count - 1) tint else tint.copy(alpha = 0.3f)
                )
            }
        }
    }
}

/** One slide in a read-only editor, the way `SiteScreen` shows a published document. */
@Composable
private fun SlideContent(
    slide: Slide,
    slideId: String,
    isDarkTheme: Boolean,
    drawersFactory: DrawersFactory
) {
    val listState = rememberLazyListState()
    val drawConfig = remember { DrawConfigFactory.getDrawConfig() }

    val manager = remember(slideId) {
        WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = Dispatchers.Default,
            selectionState = MutableStateFlow(false),
            keyboardEventFlow = MutableStateFlow(null)
        ).apply {
            loadDocument(slide.asDocument(slideId))
        }
    }

    val storyState by manager.toDraw.collectAsState(initial = DrawState())

    val drawers = drawersFactory.create(
        manager = manager,
        editable = false,
        isDarkTheme = isDarkTheme,
        drawConfig = drawConfig
    )

    WriteopiaEditor(
        modifier = Modifier.widthIn(max = 760.dp),
        editable = false,
        listState = listState,
        drawers = drawers,
        storyState = storyState
    )
}

/** The slide as a document the editor can load: the title as a `TITLE` step, the content after it. */
private fun Slide.asDocument(id: String): Document {
    val now = Clock.System.now()
    val content = buildMap {
        put(0.0, StoryStep(id = "$id-title", type = StoryTypes.TITLE.type, text = title))
        this@asDocument.content.forEachIndexed { index, step -> put((index + 1).toDouble(), step) }
    }
    return Document(
        id = id,
        title = title,
        content = content,
        createdAt = now,
        lastUpdatedAt = now,
        lastSyncedAt = null,
        workspaceId = "",
        parentId = ""
    )
}
