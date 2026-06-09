package io.writeopia.notes.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.account.ui.SettingsDialog
import io.writeopia.common.utils.ChooseNoteRoute
import io.writeopia.common.utils.EditorRoute
import io.writeopia.common.utils.MainAppRoute
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.NotesNavigationType
import io.writeopia.common.utils.Route
import io.writeopia.documents.graph.di.DocumentsGraphInjection
import io.writeopia.drawing.di.DrawingInjection
import io.writeopia.editor.di.EditorKmpInjector
import io.writeopia.features.search.di.KmpSearchInjection
import io.writeopia.features.search.ui.SearchDialog
import io.writeopia.global.shell.SideGlobalMenu
import io.writeopia.global.shell.di.SideMenuKmpInjector
import io.writeopia.global.shell.viewmodel.GlobalShellViewModel
import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.isDarkTheme
import io.writeopia.navigation.Navigation
import io.writeopia.navigation.rememberWriteopiaNavBackStack
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.notemenu.di.NotesMenuKmpInjection
import io.writeopia.notemenu.ui.screen.menu.EditFileDialog
import io.writeopia.notemenu.ui.screen.menu.RoundedVerticalDivider
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.components.multiselection.DragSelectionBox
import io.writeopia.ui.draganddrop.target.DraggableScreen
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun DesktopApp(
    selectionState: StateFlow<Boolean>,
    keyboardEventFlow: Flow<KeyboardEvent>,
    colorThemeOption: StateFlow<ColorThemeOption?>,
    accentColorOption: StateFlow<AccentColor?>,
    coroutineScope: CoroutineScope,
    selectColorTheme: (ColorThemeOption) -> Unit,
    selectAccentColor: (AccentColor) -> Unit,
    toggleMaxScreen: () -> Unit,
    navigateToRegister: () -> Unit,
    navigateToResetPassword: () -> Unit,
    modifier: Modifier = Modifier,
    hasGlobalHeader: Boolean = true,
    startDestination: Route = MainAppRoute,
) {
    val editorInjector = remember {
        EditorKmpInjector.desktop(
            selectionState = selectionState,
            keyboardEventFlow = keyboardEventFlow,
        )
    }

    val notesMenuInjection = remember {
        NotesMenuKmpInjection.desktop(
            selectionState = selectionState,
            keyboardEventFlow = keyboardEventFlow,
        )
    }

    val sideMenuInjector = remember {
        SideMenuKmpInjector()
    }

    val drawingInjection = remember { DrawingInjection() }

    val documentsGraphInjection =
        DocumentsGraphInjection(repositoryInjection = RepositoryInjector.singleton())

    val globalShellViewModel: GlobalShellViewModel =
        sideMenuInjector.provideSideMenuViewModel(keyboardEventFlow)
    val colorTheme by colorThemeOption.collectAsState()
    val accentColor by accentColorOption.collectAsState()
    val searchViewModel = KmpSearchInjection.singleton().provideViewModel()

    // Use Navigation 3's backStack
    val backStack: NavBackStack<NavKey> = rememberWriteopiaNavBackStack(startDestination)

    LaunchedEffect("initGlobalShellViewModel") {
        globalShellViewModel.init()
    }

    // Observe backStack changes to update NotesNavigationUseCase
    LaunchedEffect(backStack) {
        // Use snapshotFlow to observe backStack changes
        androidx.compose.runtime.snapshotFlow { backStack.lastOrNull() }
            .collect { currentRoute ->
                when (currentRoute) {
                    is ChooseNoteRoute -> {
                        val notesNavigation = NotesNavigation.fromType(
                            NotesNavigationType.fromType(currentRoute.navigationType),
                            currentRoute.navigationPath
                        )
                        NotesNavigationUseCase.singleton().setNoteNavigation(notesNavigation)
                    }
                    is MainAppRoute -> {
                        NotesNavigationUseCase.singleton().setNoteNavigation(NotesNavigation.Root)
                    }
                    else -> { /* No-op for other routes */ }
                }
            }
    }

    WriteopiaTheme(
        darkTheme = colorTheme.isDarkTheme(),
        accentColor = accentColor ?: AccentColor.PURPLE
    ) {
        val density = LocalDensity.current
        val globalBackground = WriteopiaTheme.colorScheme.globalBackground
        DragSelectionBox(modifier = modifier) {
            DraggableScreen {
                Row(Modifier.background(globalBackground)) {
                    val sideMenuWidth by globalShellViewModel.showSideMenuState.collectAsState()

                    SideGlobalMenu(
                        modifier = Modifier.fillMaxHeight(),
                        foldersState = globalShellViewModel.sideMenuItems,
                        width = density.run { sideMenuWidth.toDp() },
                        homeClick = {
                            val currentRoute = backStack.lastOrNull()
                            val isNotRoot = currentRoute !is MainAppRoute &&
                                !(currentRoute is ChooseNoteRoute &&
                                    currentRoute.navigationType == NotesNavigationType.ROOT.type)

                            if (isNotRoot) {
                                backStack.add(MainAppRoute)
                            }
                        },
                        favoritesClick = {
                            val currentRoute = backStack.lastOrNull()
                            val isNotFavorites = !(currentRoute is ChooseNoteRoute &&
                                currentRoute.navigationType == NotesNavigationType.FAVORITES.type)

                            if (isNotFavorites) {
                                backStack.add(
                                    ChooseNoteRoute(
                                        navigationType = NotesNavigationType.FAVORITES.type,
                                        navigationPath = ""
                                    )
                                )
                            }
                        },
                        settingsClick = globalShellViewModel::showSettings,
                        addFolder = globalShellViewModel::addFolder,
                        editFolder = globalShellViewModel::editFolder,
                        navigateToFolder = { id ->
                            backStack.add(
                                ChooseNoteRoute(
                                    navigationType = NotesNavigationType.FOLDER.type,
                                    navigationPath = id
                                )
                            )
                        },
                        navigateToEditDocument = { id, title ->
                            backStack.add(EditorRoute(noteId = id, noteTitle = title))
                        },
                        moveRequest = globalShellViewModel::moveToFolder,
                        expandFolder = globalShellViewModel::expandFolder,
                        searchClick = globalShellViewModel::showSearch,
                        highlightContent = {},
                        changeIcon = globalShellViewModel::changeIcons,
                        toggleMaxScreen = toggleMaxScreen
                    )

                    Column {
                        if (hasGlobalHeader) {
                            GlobalHeader(
                                canNavigateBack = backStack.size > 1,
                                onNavigateBack = { backStack.removeLastOrNull() },
                                pathState = globalShellViewModel.folderPath,
                                toggleMaxScreen = toggleMaxScreen
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Navigation(
                                isDarkTheme = colorTheme.isDarkTheme(),
                                startDestination = startDestination,
                                notesMenuInjection = notesMenuInjection,
                                sideMenuKmpInjector = sideMenuInjector,
                                documentsGraphInjection = documentsGraphInjection,
                                editorInjector = editorInjector,
                                drawingInjection = drawingInjection,
                                selectedColorTheme = colorThemeOption,
                                selectedAccentColor = accentColorOption,
                                selectColorTheme = selectColorTheme,
                                selectAccentColor = selectAccentColor,
                                onDrawingSaved = { documentId, storyStepId, drawingData ->
                                    editorInjector.addDrawingToDocument(
                                        documentId,
                                        storyStepId,
                                        drawingData
                                    )
                                },
                                navigationBar = {},
                                externalBackStack = backStack
                            )

                            val folderEdit =
                                globalShellViewModel.editFolderState.collectAsState().value

                            if (folderEdit != null) {
                                EditFileDialog(
                                    folderEdit = folderEdit,
                                    onDismissRequest = globalShellViewModel::stopEditingFolder,
                                    deleteFolder = globalShellViewModel::deleteFolder,
                                    editFolder = globalShellViewModel::updateFolder
                                )
                            }

                            val showSettingsState by globalShellViewModel
                                .showSettingsState
                                .collectAsState()

                            if (showSettingsState) {
                                SettingsDialog(
                                    workplacePathState = globalShellViewModel.workspaceLocalPath,
                                    selectedColorTheme = colorThemeOption,
                                    selectedAccentColor = accentColorOption,
                                    ollamaUrlState = globalShellViewModel.ollamaUrl,
                                    ollamaAvailableModels = globalShellViewModel.modelsForUrl,
                                    ollamaSelectedModel = globalShellViewModel.ollamaSelectedModelState,
                                    downloadModelState = globalShellViewModel.downloadModelState,
                                    userOnlineState = globalShellViewModel.userState,
                                    showDeleteConfirmation = globalShellViewModel.showDeleteConfirmation,
                                    syncWorkspaceState = globalShellViewModel.lastWorkspaceSync,
                                    isAutoSyncEnabled = globalShellViewModel.isAutoSyncEnabled,
                                    workspaceToEdit = globalShellViewModel.workspaceToEdit,
                                    onDismissRequest = globalShellViewModel::hideSettings,
                                    selectColorTheme = selectColorTheme,
                                    selectAccentColor = selectAccentColor,
                                    workspaces = globalShellViewModel.availableWorkspaces,
                                    selectWorkplacePath = globalShellViewModel::changeWorkspaceLocalPath,
                                    ollamaUrlChange = globalShellViewModel::changeOllamaUrl,
                                    ollamaModelChange = globalShellViewModel::selectOllamaModel,
                                    ollamaModelsRetry = globalShellViewModel::retryModels,
                                    downloadModel = globalShellViewModel::modelToDownload,
                                    deleteModel = globalShellViewModel::deleteModel,
                                    signIn = navigateToRegister,
                                    resetPassword = navigateToResetPassword,
                                    logout = {
                                        globalShellViewModel.logout(sideEffect = navigateToRegister)
                                    },
                                    showDeleteConfirm = globalShellViewModel::showDeleteConfirm,
                                    dismissDeleteConfirm = globalShellViewModel::dismissDeleteConfirm,
                                    deleteAccount = {
                                        globalShellViewModel.deleteAccount(
                                            sideEffect = navigateToRegister
                                        )
                                    },
                                    syncWorkspace = globalShellViewModel::syncWorkspace,
                                    onAutoSyncToggle = globalShellViewModel::toggleAutoSync,
                                    addUserToTeam = globalShellViewModel::addUserToTeam,
                                    selectWorkspaceToManage =
                                        globalShellViewModel::selectWorkspaceToManage,
                                    usersInSelectedWorkspace = globalShellViewModel.usersOfWorkspaceToEdit,
                                )
                            }

                            val showSearchState by globalShellViewModel
                                .showSearchDialog
                                .collectAsState()

                            if (showSearchState) {
                                LaunchedEffect(true) {
                                    searchViewModel.init()
                                }

                                SearchDialog(
                                    searchState = searchViewModel.searchState,
                                    searchResults = searchViewModel.queryResults,
                                    onSearchType = searchViewModel::onSearchType,
                                    onDismissRequest = globalShellViewModel::hideSearch,
                                    documentClick = { id, title ->
                                        backStack.add(EditorRoute(noteId = id, noteTitle = title))
                                    },
                                    onFolderClick = { navigation ->
                                        backStack.add(
                                            ChooseNoteRoute(
                                                navigationType = navigation.navigationType.type,
                                                navigationPath = if (navigation is NotesNavigation.Folder) navigation.id else ""
                                            )
                                        )
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(10.dp)
                                    .align(alignment = Alignment.CenterStart)
                                    .draggable(
                                        orientation = Orientation.Horizontal,
                                        state = rememberDraggableState { delta ->
                                            globalShellViewModel.moveSideMenu(sideMenuWidth + delta)
                                        },
                                        onDragStopped = {
                                            globalShellViewModel.saveMenuWidth()
                                        },
                                    )
                                    .pointerHoverIcon(PointerIcon.Crosshair),
                            )

                            Box(
                                modifier = Modifier
                                    .height(60.dp)
                                    .width(24.dp)
                                    .align(alignment = Alignment.CenterStart)
                                    .clip(RoundedCornerShape(100))
                                    .clickable(onClick = globalShellViewModel::toggleSideMenu)
                                    .padding(top = 10.dp, bottom = 10.dp, start = 8.dp, end = 16.dp)
                                    .draggable(
                                        orientation = Orientation.Horizontal,
                                        state = rememberDraggableState { delta ->
                                            globalShellViewModel.moveSideMenu(sideMenuWidth + delta)
                                        },
                                        onDragStopped = {
                                            globalShellViewModel.saveMenuWidth()
                                        },
                                    )
                                    .pointerHoverIcon(PointerIcon.Crosshair),
                            ) {
                                RoundedVerticalDivider(
                                    modifier = Modifier.height(60.dp).align(Alignment.Center),
                                    thickness = 4.dp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4F)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
