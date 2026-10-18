package io.writeopia.global.shell

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.env.EnvUtils
import io.writeopia.common.utils.icons.IconChange
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.commonui.folders.documentList
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val FINAL_WIDTH = 300

/** Clears the macOS window buttons, which sit over the top left of the full-content window. */
private const val MAC_WINDOW_BUTTONS_WIDTH = 80

const val SIDE_MENU_SYNCING_TAG = "SideMenuSyncing"
const val SIDE_MENU_LOADING_DOCUMENT_TAG = "SideMenuLoadingDocument"

@Composable
fun SideGlobalMenu(
    modifier: Modifier = Modifier,
    foldersState: StateFlow<List<MenuItemUi>>,
    background: Color = WriteopiaTheme.colorScheme.globalBackground,
    width: Dp = FINAL_WIDTH.dp,
    searchClick: () -> Unit,
    homeClick: () -> Unit,
    favoritesClick: () -> Unit,
    settingsClick: () -> Unit,
    addFolder: () -> Unit,
    highlightContent: () -> Unit,
    graphClick: (() -> Unit)? = null,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    navigateToFolder: (String) -> Unit,
    navigateToEditDocument: (String, String) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    expandFolder: (String) -> Unit,
    changeIcon: (String, String, Int, IconChange) -> Unit,
    toggleMaxScreen: () -> Unit,
    isSyncingState: StateFlow<Boolean> = MutableStateFlow(false),
    isLoadingDocumentState: StateFlow<Boolean> = MutableStateFlow(false),
) {
    val widthState by derivedStateOf { width }

    val widthAnimatedState by animateDpAsState(widthState)
    val showContent by derivedStateOf {
        when {
            widthState > 80.dp -> ShowContent.FULL
            widthState > 40.dp -> ShowContent.ICONS
            else -> ShowContent.HIDE
        }
    }

    Row(
        modifier = modifier.background(background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(widthAnimatedState).fillMaxHeight()) {
            if (showContent != ShowContent.HIDE) {
                val menuItems by foldersState.collectAsState()

                LazyColumn(
                    horizontalAlignment = if (showContent == ShowContent.ICONS) {
                        Alignment.CenterHorizontally
                    } else {
                        Alignment.Start
                    },
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    item {
                        // The strip under the window title bar, where a spinner shows while
                        // the app syncs with the cloud.
                        Box(
                            modifier = Modifier.height(48.dp).fillMaxWidth()
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onDoubleClick = toggleMaxScreen,
                                    onClick = {}
                                )
                        ) {
                            SyncingIndicator(
                                isSyncingState = isSyncingState,
                                showContent = showContent,
                                modifier = Modifier.align(
                                    if (showContent == ShowContent.FULL) {
                                        Alignment.CenterStart
                                    } else {
                                        Alignment.Center
                                    }
                                )
                            )
                        }
                    }

                    item {
                        SettingsOptions(
                            showContent = showContent,
                            iconVector = WrIcons.search,
                            contentDescription = WrStrings.search(),
                            text = WrStrings.search(),
                            click = searchClick
                        )
                    }

                    item {
                        SettingsOptions(
                            showContent = showContent,
                            iconVector = WrIcons.home,
                            contentDescription = WrStrings.home(),
                            text = WrStrings.home(),
                            click = homeClick
                        )
                    }

                    item {
                        SettingsOptions(
                            showContent = showContent,
                            iconVector = WrIcons.favorites,
                            contentDescription = WrStrings.favorites(),
                            text = WrStrings.favorites(),
                            click = favoritesClick
                        )
                    }

                    if (graphClick != null) {
                        item {
                            SettingsOptions(
                                showContent = showContent,
                                iconVector = WrIcons.chart,
                                contentDescription = WrStrings.documentsGraph(),
                                text = WrStrings.documentsGraph(),
                                click = graphClick
                            )
                        }
                    }

                    item {
                        SettingsOptions(
                            showContent = showContent,
                            iconVector = WrIcons.settings,
                            contentDescription = WrStrings.settings(),
                            text = WrStrings.settings(),
                            click = settingsClick,
                        )
                    }

                    if (showContent == ShowContent.FULL) {
                        item {
                            val isLoadingDocument by isLoadingDocumentState.collectAsState()

                            title(
                                text = WrStrings.folder(),
                                isLoading = isLoadingDocument,
                                trailingContent = {
                                    Icon(
                                        imageVector = WrIcons.target,
                                        contentDescription = "Select opened file",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = highlightContent)
                                            .padding(6.dp)
                                    )

                                    Icon(
                                        imageVector = WrIcons.addCircle,
                                        contentDescription = "Add Folder",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = addFolder)
                                            .padding(6.dp)
                                    )
                                }
                            )
                        }

                        documentList(
                            menuItems = menuItems,
                            editFolder = editFolder,
                            selectedFolder = navigateToFolder,
                            selectedDocument = navigateToEditDocument,
                            moveRequest = moveRequest,
                            expandFolder = expandFolder,
                            changeIcon = changeIcon
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOptions(
    showContent: ShowContent,
    iconVector: ImageVector?,
    contentDescription: String,
    text: String,
    click: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .let { modifierLet ->
                if (click != null) {
                    modifierLet.clickable(onClick = click)
                } else {
                    modifierLet
                }
            }
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            .let { modifierLet ->
                if (showContent == ShowContent.FULL) {
                    modifierLet.fillMaxWidth()
                } else {
                    modifierLet
                }
            }
    ) {
        iconVector?.let { icon ->
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        if (showContent == ShowContent.FULL) {
            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = text,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )

            if (trailingContent != null) {
                Spacer(modifier = Modifier.weight(1F))

                trailingContent()
            }
        }
    }
}

/** A section header. With [isLoading], a small spinner sits right by the [text]. */
@Composable
private fun title(
    text: String,
    click: (() -> Unit)? = null,
    isLoading: Boolean = false,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.let { modifierLet ->
            if (click != null) {
                modifierLet.clickable(onClick = click)
            } else {
                modifierLet
            }
        }
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(12.dp)
                    .testTag(SIDE_MENU_LOADING_DOCUMENT_TAG),
                color = MaterialTheme.colorScheme.onBackground,
                strokeWidth = 2.dp
            )
        }

        Spacer(modifier = Modifier.weight(1F))

        if (trailingContent != null) {
            trailingContent()
        }
    }
}

/** A small spinner, shown only while a sync with the backend runs. */
@Composable
private fun SyncingIndicator(
    isSyncingState: StateFlow<Boolean>,
    showContent: ShowContent,
    modifier: Modifier = Modifier
) {
    val isSyncing by isSyncingState.collectAsState()
    if (!isSyncing) return

    val startPadding = when {
        showContent != ShowContent.FULL -> 0.dp
        EnvUtils.isMacOs() -> MAC_WINDOW_BUTTONS_WIDTH.dp
        else -> 16.dp
    }

    CircularProgressIndicator(
        modifier = modifier
            .padding(start = startPadding)
            .size(14.dp)
            .testTag(SIDE_MENU_SYNCING_TAG),
        color = MaterialTheme.colorScheme.onBackground,
        strokeWidth = 2.dp
    )
}

@Preview
@Composable
fun SideGlobalMenuPreview() {
    SideGlobalMenu(
        modifier = Modifier.background(Color.Cyan),
        foldersState = MutableStateFlow(emptyList()),
        searchClick = {},
        homeClick = {},
        favoritesClick = {},
        settingsClick = {},
        addFolder = {},
        highlightContent = {},
        editFolder = {},
        navigateToFolder = {},
        navigateToEditDocument = { _, _ -> },
        moveRequest = { _, _ -> },
        expandFolder = {},
        changeIcon = { _, _, _, _ -> },
        toggleMaxScreen = {}
    )
}

private enum class ShowContent {
    HIDE,
    ICONS,
    FULL
}
