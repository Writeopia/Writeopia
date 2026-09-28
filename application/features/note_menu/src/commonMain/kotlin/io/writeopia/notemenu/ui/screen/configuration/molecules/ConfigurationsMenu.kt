package io.writeopia.notemenu.ui.screen.configuration.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.options.slide.HorizontalOptions
import io.writeopia.notemenu.ui.screen.configuration.modifier.orderConfigModifierHorizontal
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val INNER_PADDING = 3

/**
 * Bottom sheet with how the documents are shown and sorted and, inside a folder, editing, moving
 * and deleting it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileConfigurationsMenu(
    dialogMaxWidth: Dp = 500.dp,
    selected: Flow<Int>,
    visibilityState: Boolean,
    outsideClick: () -> Unit,
    staggeredGridOptionClick: () -> Unit,
    gridOptionClick: () -> Unit,
    listOptionClick: () -> Unit,
    sortingSelected: (OrderBy) -> Unit,
    sortingState: StateFlow<OrderBy>,
    modifier: Modifier = Modifier,
    folderTitle: String? = null,
    onEditFolder: () -> Unit = {},
    onMoveFolder: () -> Unit = {},
    onDeleteFolder: () -> Unit = {},
) {
    if (!visibilityState) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Folder actions open a dialog, so the sheet slides away first.
    val hideThen = { action: () -> Unit ->
        {
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { action() }
            Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = outsideClick,
        sheetState = sheetState,
        sheetMaxWidth = dialogMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        ConfigurationsMenuContent(
            selected = selected,
            staggeredGridOptionClick = staggeredGridOptionClick,
            gridOptionClick = gridOptionClick,
            listOptionClick = listOptionClick,
            sortingSelected = sortingSelected,
            sortingState = sortingState,
            folderTitle = folderTitle,
            onEditFolder = hideThen(onEditFolder),
            onMoveFolder = hideThen(onMoveFolder),
            onDeleteFolder = hideThen(onDeleteFolder),
        )
    }
}

@Composable
private fun ConfigurationsMenuContent(
    selected: Flow<Int>,
    staggeredGridOptionClick: () -> Unit,
    gridOptionClick: () -> Unit,
    listOptionClick: () -> Unit,
    sortingSelected: (OrderBy) -> Unit,
    sortingState: StateFlow<OrderBy>,
    folderTitle: String?,
    onEditFolder: () -> Unit,
    onMoveFolder: () -> Unit,
    onDeleteFolder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ArrangementSection(selected, staggeredGridOptionClick, gridOptionClick, listOptionClick)

        SortingSection(sortingSelected = sortingSelected, sortingState)

        FolderSection(
            folderTitle = folderTitle,
            onEditFolder = onEditFolder,
            onMoveFolder = onMoveFolder,
            onDeleteFolder = onDeleteFolder,
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SectionText(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 18.sp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ArrangementOptions(
    selected: Flow<Int>,
    staggeredGridOptionClick: () -> Unit,
    gridOptionClick: () -> Unit,
    listOptionClick: () -> Unit,
    height: Dp = 42.dp,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    HorizontalOptions(
        selectedState = selected,
        options = listOf<Pair<() -> Unit, @Composable RowScope.() -> Unit>>(
            staggeredGridOptionClick to {
                Icon(
                    modifier = Modifier
                        .orderConfigModifierHorizontal(clickable = gridOptionClick)
                        .weight(1F)
                        .zIndex(1000F),
                    imageVector = WrIcons.layoutStaggeredGrid,
                    contentDescription = "staggered card",
                    //            stringResource(R.string.staggered_card),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            gridOptionClick to {
                Icon(
                    modifier = Modifier
                        .orderConfigModifierHorizontal(clickable = gridOptionClick)
                        .weight(1F),
                    imageVector = WrIcons.layoutGrid,
                    contentDescription = "staggered card",
                    //            stringResource(R.string.staggered_card),
                    tint = tint
                )
            },
            listOptionClick to {
                Icon(
                    modifier = Modifier
                        .orderConfigModifierHorizontal(clickable = listOptionClick)
                        .weight(1F),
                    imageVector = WrIcons.layoutList,
                    contentDescription = "note list",
                    //            stringResource(R.string.note_list),
                    tint = tint
                )
            }
        ),
        modifier = Modifier.fillMaxWidth().padding(INNER_PADDING.dp),
        height = height
    )
}

@Composable
private fun ArrangementSection(
    selected: Flow<Int>,
    staggeredGridOptionClick: () -> Unit,
    gridOptionClick: () -> Unit,
    listOptionClick: () -> Unit
) {
    SectionText(text = WrStrings.arrangement())

    ArrangementOptions(
        selected = selected,
        staggeredGridOptionClick = staggeredGridOptionClick,
        gridOptionClick = gridOptionClick,
        listOptionClick = listOptionClick,
    )
}

@Composable
private fun SortingSection(sortingSelected: (OrderBy) -> Unit, sortingState: StateFlow<OrderBy>) {
    val order by sortingState.collectAsState()

    SectionText(text = WrStrings.sorting())
    val optionStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )

    val background = @Composable { orderBy: OrderBy ->
        if (orderBy == order) {
            WriteopiaTheme.colorScheme.highlight
        } else {
            WriteopiaTheme.colorScheme.defaultButton
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = INNER_PADDING.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(WriteopiaTheme.colorScheme.defaultButton)
    ) {
        Text(
            modifier = Modifier
                .background(background(OrderBy.UPDATE))
                .clickable { sortingSelected(OrderBy.UPDATE) }
                .sortingOptionModifier(),
            text = WrStrings.lastUpdated(),
//            stringResource(R.string.last_updated)
            style = optionStyle,
        )

        HorizontalDivider(color = WriteopiaTheme.colorScheme.highlight)

        Text(
            modifier = Modifier
                .background(background(OrderBy.CREATE))
                .clickable { sortingSelected(OrderBy.CREATE) }
                .sortingOptionModifier(),
            text = WrStrings.created(),
//            stringResource(R.string.last_created),
            style = optionStyle,
        )

        HorizontalDivider(color = WriteopiaTheme.colorScheme.highlight)

        Text(
            modifier = Modifier
                .background(background(OrderBy.NAME))
                .clickable { sortingSelected(OrderBy.NAME) }
                .sortingOptionModifier(),
            text = WrStrings.sortByName(),
//            stringResource(R.string.name),
            style = optionStyle,
        )
    }
}

private fun Modifier.sortingOptionModifier(): Modifier = fillMaxWidth().padding(12.dp)

/**
 * Editing, moving and deleting the folder being displayed. Outside a folder ([folderTitle] null)
 * there's nothing to act on, so nothing is shown.
 */
@Composable
private fun FolderSection(
    folderTitle: String?,
    onEditFolder: () -> Unit,
    onMoveFolder: () -> Unit,
    onDeleteFolder: () -> Unit,
) {
    if (folderTitle == null) return

    SectionText(text = folderTitle.ifBlank { "Folder" })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = INNER_PADDING.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(WriteopiaTheme.colorScheme.defaultButton)
    ) {
        FolderAction(
            text = "Edit folder",
            icon = WrIcons.edit,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onEditFolder,
        )

        HorizontalDivider(color = WriteopiaTheme.colorScheme.highlight)

        FolderAction(
            text = "Move to…",
            icon = WrIcons.move,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onMoveFolder,
        )

        HorizontalDivider(color = WriteopiaTheme.colorScheme.highlight)

        FolderAction(
            text = "Delete folder",
            icon = WrIcons.delete,
            color = MaterialTheme.colorScheme.error,
            onClick = onDeleteFolder,
        )
    }
}

@Composable
private fun FolderAction(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).sortingOptionModifier(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = null,
            tint = color,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Preview
@Composable
private fun ConfigurationsMenu_Preview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        ConfigurationsMenuContent(
            selected = MutableStateFlow(1),
            staggeredGridOptionClick = {},
            gridOptionClick = {},
            listOptionClick = {},
            sortingSelected = {},
            sortingState = MutableStateFlow(OrderBy.NAME),
            folderTitle = "Folder",
            onEditFolder = {},
            onMoveFolder = {},
            onDeleteFolder = {},
        )
    }
}

@Preview
@Composable
private fun ArrangementOptions_Preview() {
    ArrangementOptions(MutableStateFlow(1), {}, {}, {})
}
