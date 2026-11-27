package io.writeopia.notes.desktop.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.coroutines.flow.flowOf

/** Reads the names tabs show: a document's title as it changes, a folder's name. */
class TabTitles(
    private val documentRepository: () -> DocumentRepository,
    private val folderRepository: () -> FolderRepository,
) {
    @Composable
    fun title(tab: DesktopTab): String {
        val place = tab.place
        return when (place) {
            TabPlace.Root -> HOME
            TabPlace.Other -> tab.route?.let(::labelOfRoute) ?: HOME
            is TabPlace.Document -> {
                val info by produceState<String?>(null, place.id) {
                    val flow = runCatching {
                        documentRepository().listenForDocumentInfoById(place.id)
                    }.getOrDefault(flowOf(null))
                    flow.collect { value = it?.title }
                }
                (info ?: place.title).takeIf { it.isNotBlank() && it != "null" } ?: UNTITLED
            }

            is TabPlace.Folder -> {
                val name by produceState<String?>(null, place.id) {
                    value = runCatching { folderRepository().getFolderById(place.id)?.title }.getOrNull()
                }
                name ?: FOLDER
            }
        }
    }

    private fun labelOfRoute(route: String): String = when {
        route.startsWith("note_details") -> UNTITLED
        route.startsWith("documents_graph") -> "Graph"
        route.startsWith("search") -> "Search"
        route.startsWith("notifications") -> "Notifications"
        route.contains("favorites") -> "Favorites"
        else -> HOME
    }

    private companion object {
        const val HOME = "Home"
        const val UNTITLED = "Untitled"
        const val FOLDER = "Folder"
    }
}

private val TAB_MIN_WIDTH = 120.dp
private val TAB_MAX_WIDTH = 220.dp

/**
 * The row of open tabs over the content. A click shows a tab, the middle button or the × closes
 * it, a long press and a drag moves it, + opens a new one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopTabStrip(
    tabsState: DesktopTabsState,
    titles: TabTitles,
    modifier: Modifier = Modifier,
    /** The strip is the title bar of the window: a double click on its empty space zooms it. */
    onEmptySpaceDoubleClick: () -> Unit = {},
) {
    val tabs by tabsState.tabs.collectAsState()
    val activeId by tabsState.activeId.collectAsState()
    val listState = rememberLazyListState()

    // Keep the active tab in sight when it changes from a shortcut or from Claude Code.
    LaunchedEffect(activeId) {
        val index = tabs.indexOfFirst { it.id == activeId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onDoubleClick = onEmptySpaceDoubleClick,
                onClick = {}
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1F, fill = false),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                TabItem(
                    tab = tab,
                    title = titles.title(tab),
                    isActive = tab.id == activeId,
                    onClick = { tabsState.activate(tab.id) },
                    onClose = { tabsState.close(tab.id) },
                    onDragBy = { slots -> tabsState.move(index, (index + slots).coerceIn(0, tabs.lastIndex)) },
                )
            }
        }

        Icon(
            imageVector = WrIcons.add,
            contentDescription = "New tab",
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7F),
            modifier = Modifier
                .padding(start = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { tabsState.open(TabPlace.Root) }
                .padding(5.dp)
                .size(16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabItem(
    tab: DesktopTab,
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onDragBy: (Int) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    var dragX by remember { mutableFloatStateOf(0F) }
    var slotWidth by remember { mutableStateOf(1F) }

    val background = when {
        isActive -> MaterialTheme.colorScheme.background
        isHovered -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06F)
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0F)
    }

    Row(
        modifier = Modifier
            .widthIn(min = TAB_MIN_WIDTH, max = TAB_MAX_WIDTH)
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Tertiary), onClick = onClose)
            .pointerInput(tab.id) {
                slotWidth = size.width.toFloat()
                detectDragGesturesAfterLongPress(
                    onDragEnd = {
                        val slots = (dragX / slotWidth).toInt()
                        if (slots != 0) onDragBy(slots)
                        dragX = 0F
                    },
                    onDragCancel = { dragX = 0F },
                ) { change, amount ->
                    change.consume()
                    dragX += amount.x
                }
            }
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (tab.place is TabPlace.Folder) WrIcons.folder else WrIcons.file,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6F),
            modifier = Modifier.size(14.dp),
        )

        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isActive) 1F else 0.7F),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1F, fill = false).padding(start = 6.dp),
        )

        Spacer(Modifier.weight(1F, fill = true).widthIn(min = 4.dp))

        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (isActive || isHovered) {
                Icon(
                    imageVector = WrIcons.close,
                    contentDescription = "Close tab",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6F),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onClose)
                        .padding(3.dp)
                        .size(12.dp),
                )
            }
        }
    }
}
