package io.writeopia.notes.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.writeopia.account.ui.SettingsDialog
import io.writeopia.common.utils.Destinations
import io.writeopia.editor.di.EditorKmpInjector
import io.writeopia.features.search.di.KmpSearchInjection
import io.writeopia.features.search.ui.SearchDialog
import io.writeopia.global.shell.SideGlobalMenu
import io.writeopia.global.shell.di.SideMenuKmpInjector
import io.writeopia.global.shell.viewmodel.GlobalShellViewModel
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.isDarkTheme
import io.writeopia.navigation.Navigation
import io.writeopia.navigation.notes.navigateToFolder
import io.writeopia.navigation.notes.navigateToNote
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.NotesNavigationType
import io.writeopia.documents.graph.di.DocumentsGraphInjection
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.notemenu.di.NotesMenuKmpInjection
import io.writeopia.notemenu.navigation.NAVIGATION_PATH
import io.writeopia.notemenu.navigation.NAVIGATION_TYPE
import io.writeopia.notemenu.navigation.navigateToNotes
import io.writeopia.notemenu.ui.screen.menu.EditFileScreen
import io.writeopia.notemenu.ui.screen.menu.RoundedVerticalDivider
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.di.SqlDelightDaoInjector
import io.writeopia.sqldelight.di.WriteopiaDbInjector
import io.writeopia.theme.WrieopiaTheme
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.components.multiselection.DragSelectionBox
import io.writeopia.ui.draganddrop.target.DraggableScreen
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun DesktopApp(
    writeopiaDb: WriteopiaDb? = null,
    selectionState: StateFlow<Boolean>,
    keyboardEventFlow: Flow<KeyboardEvent>,
    colorThemeOption: StateFlow<ColorThemeOption?>,
    coroutineScope: CoroutineScope,
    selectColorTheme: (ColorThemeOption) -> Unit,
    toggleMaxScreen: () -> Unit,
    navigateToRegister: () -> Unit,
    startDestination: String = startDestination(),
) {
    WriteopiaConnectionInjector.setBaseUrl("http://localhost:8080")

    if (writeopiaDb != null) {
        WriteopiaDbInjector.initialize(writeopiaDb)
    }

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

    val documentsGraphInjection =
        DocumentsGraphInjection(repositoryInjection = SqlDelightDaoInjector.singleton())

    val globalShellViewModel: GlobalShellViewModel = sideMenuInjector.provideSideMenuViewModel()
    val colorTheme by colorThemeOption.collectAsState()
    val navigationController: NavHostController = rememberNavController()
    val searchViewModel = KmpSearchInjection.singleton().provideViewModel()

    LaunchedEffect("initGlobalShellViewModel") {
        globalShellViewModel.init()
    }

    coroutineScope.launch {
        navigationController.currentBackStackEntryFlow.collect { navEntry ->
            val navigationType = navEntry.arguments?.getString(NAVIGATION_TYPE)
            val navigationPath = navEntry.arguments?.getString(NAVIGATION_PATH)
            if (navigationType != null && navigationPath != null) {
                NotesNavigation.fromType(
                    NotesNavigationType.fromType(navigationType),
                    navigationPath
                ).let(NotesNavigationUseCase.singleton()::setNoteNavigation)
            }
        }
    }

    WrieopiaTheme(darkTheme = colorTheme.isDarkTheme()) {
        val globalBackground = WriteopiaTheme.colorScheme.globalBackground
        DragSelectionBox {
            DraggableScreen {
                Row(Modifier.background(globalBackground)) {
                    val sideMenuWidth by globalShellViewModel.showSideMenuState.collectAsState()

                    SideGlobalMenu(
                        modifier = Modifier.fillMaxHeight(),
                        foldersState = globalShellViewModel.sideMenuItems,
                        width = sideMenuWidth.dp,
                        homeClick = {
//                            val navType = navigationController.currentBackStackEntry
//                                ?.arguments
//                                ?.getString(NAVIGATION_TYPE)
//                                ?.let(NotesNavigationType::fromType)
//
//                            if (navType != NotesNavigationType.ROOT) {
//                                navigationController.navigateToNotes(NotesNavigation.Root)
//                            }

                            navigateToRegister()
                        },
                        favoritesClick = {
                            val navType = navigationController.currentBackStackEntry
                                ?.arguments
                                ?.getString(NAVIGATION_TYPE)
                                ?.let(NotesNavigationType::fromType)

                            if (navType != NotesNavigationType.FAVORITES) {
                                navigationController.navigateToNotes(NotesNavigation.Favorites)
                            }
                        },
                        settingsClick = globalShellViewModel::showSettings,
                        addFolder = globalShellViewModel::addFolder,
                        editFolder = globalShellViewModel::editFolder,
                        navigateToFolder = { id ->
                            val navigation = NotesNavigation.Folder(id)
                            navigationController.navigateToNotes(navigation)
                        },
                        navigateToEditDocument = navigationController::navigateToNote,
                        moveRequest = globalShellViewModel::moveToFolder,
                        expandFolder = globalShellViewModel::expandFolder,
                        searchClick = globalShellViewModel::showSearch,
                        highlightContent = {},
                        changeIcon = globalShellViewModel::changeIcons,
                        toggleMaxScreen = toggleMaxScreen
                    )

                    Column {
                        GlobalHeader(
                            navigationController,
                            globalShellViewModel.folderPath,
                            toggleMaxScreen
                        )

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
                                selectColorTheme = selectColorTheme,
                                navController = navigationController
                            ) {}

                            val folderEdit =
                                globalShellViewModel.editFolderState.collectAsState().value

                            if (folderEdit != null) {
                                EditFileScreen(
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
                                    selectedThemePosition = MutableStateFlow(2),
                                    ollamaUrlState = globalShellViewModel.ollamaUrl,
                                    ollamaAvailableModels = globalShellViewModel.modelsForUrl,
                                    ollamaSelectedModel = globalShellViewModel.ollamaSelectedModelState,
                                    downloadModelState = globalShellViewModel.downloadModelState,
                                    onDismissRequest = globalShellViewModel::hideSettings,
                                    selectColorTheme = selectColorTheme,
                                    selectWorkplacePath = globalShellViewModel::changeWorkspaceLocalPath,
                                    ollamaUrlChange = globalShellViewModel::changeOllamaUrl,
                                    ollamaModelChange = globalShellViewModel::selectOllamaModel,
                                    ollamaModelsRetry = globalShellViewModel::retryModels,
                                    downloadModel = { model ->
                                        globalShellViewModel.modelToDownload(model)
                                    },
                                    deleteModel = globalShellViewModel::deleteModel
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
                                    documentClick = navigationController::navigateToNote,
                                    onFolderClick = navigationController::navigateToFolder
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
                                    .width(16.dp)
                                    .align(alignment = Alignment.CenterStart)
                                    .clip(RoundedCornerShape(100))
                                    .clickable(onClick = globalShellViewModel::toggleSideMenu)
                                    .padding(vertical = 6.dp)
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

fun startDestination() =
    "${Destinations.CHOOSE_NOTE.id}/${NotesNavigationType.ROOT.type}/path"
