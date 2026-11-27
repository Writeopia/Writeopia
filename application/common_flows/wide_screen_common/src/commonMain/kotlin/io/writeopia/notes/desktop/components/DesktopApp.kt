package io.writeopia.notes.desktop.components

import androidx.compose.foundation.layout.fillMaxSize
import io.writeopia.claudecode.ui.ClaudeCodeAiModeBanner
import io.writeopia.claudecode.ClaudeCodeBridge
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.writeopia.account.ui.SettingsDialog
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.NotesNavigationType
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.commonui.snackbar.WriteopiaSnackbar
import io.writeopia.commonui.tabs.LocalOpenInNewTab
import io.writeopia.core.folders.di.WorkspaceInjection
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
import io.writeopia.navigation.notes.navigateToFolder
import io.writeopia.navigation.notes.navigateToNoteMobile
import io.writeopia.navigation.notes.navigateToNoteWithPresentation
import io.writeopia.navigation.notes.navigateToNoteWithSearch
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.notemenu.di.NotesMenuInjection
import io.writeopia.notemenu.di.NotesMenuKmpInjection
import io.writeopia.notemenu.navigation.NAVIGATION_PATH
import io.writeopia.notemenu.navigation.NAVIGATION_TYPE
import io.writeopia.notemenu.navigation.navigateToNotes
import io.writeopia.notemenu.ui.screen.menu.EditFileDialog
import io.writeopia.notemenu.ui.screen.menu.FolderMoveSnackbarEffect
import io.writeopia.notemenu.ui.screen.menu.RoundedVerticalDivider
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.core.folders.di.FoldersInjector
import io.writeopia.notes.desktop.tabs.DesktopTab
import io.writeopia.notes.desktop.tabs.DesktopTabStrip
import io.writeopia.notes.desktop.tabs.DesktopTabsHost
import io.writeopia.notes.desktop.tabs.DesktopTabsState
import io.writeopia.notes.desktop.tabs.TabPlace
import io.writeopia.notes.desktop.tabs.TabTitles
import io.writeopia.notes.desktop.tabs.TabsStore
import io.writeopia.notes.desktop.tabs.navigateToPlace
import io.writeopia.notes.desktop.tabs.tabPlace
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.components.multiselection.DragSelectionBox
import io.writeopia.ui.draganddrop.target.DraggableScreen
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.StateFlow

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
    navigateToChooseWorkspace: () -> Unit,
    navigateToSpaceChoice: () -> Unit,
    navigateToAccountDeletionStarted: () -> Unit,
    modifier: Modifier = Modifier,
    hasGlobalHeader: Boolean = true,
    startDestination: String = startDestination(),
    notesMenuInjection: NotesMenuInjection? = null,
    sideMenuInjector: SideMenuKmpInjector? = null,
    /** Saves the open tabs between launches and shows the tab strip; null: one tab, no strip (web). */
    tabsStore: TabsStore? = null,
    /** Where the window's key handler finds the tabs. */
    tabsHost: DesktopTabsHost? = null,
) {
    val tabsState = rememberDesktopTabs(tabsStore, tabsHost)

    // The tabs of the workspace load in a moment from the database.
    if (tabsState == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val platform = LocalPlatform.current

    // Each tab has its own injectors, fed only the keys pressed while it is the active tab: the
    // view models of the tabs out of sight stay alive and would otherwise undo, save or delete
    // in documents nobody is looking at.
    val tabInjectors = remember(tabsState) { mutableMapOf<String, TabInjectors>() }

    fun injectorsOf(tab: DesktopTab): TabInjectors = tabInjectors.getOrPut(tab.id) {
        val tabKeys = keyboardEventFlow.filter { tabsState.activeId.value == tab.id }
        val imageUploader = WorkspaceInjection.singleton().provideImageUploader()
        TabInjectors(
            editor = when (platform) {
                PlatformType.WEB -> EditorKmpInjector.web(
                    selectionState = selectionState,
                    keyboardEventFlow = tabKeys,
                    imageUploader = imageUploader,
                )

                else -> EditorKmpInjector.desktop(
                    selectionState = selectionState,
                    keyboardEventFlow = tabKeys,
                    imageUploader = imageUploader,
                )
            },
            notesMenu = notesMenuInjection ?: NotesMenuKmpInjection.desktopForTab(
                selectionState = selectionState,
                keyboardEventFlow = tabKeys,
            )
        )
    }

    val tabs by tabsState.tabs.collectAsState()
    val activeId by tabsState.activeId.collectAsState()
    val activeTab = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    val activeInjectors = injectorsOf(activeTab)
    val editorInjector = activeInjectors.editor
    val tabStateHolder = rememberSaveableStateHolder()

    // A closed tab: once it is off the screen (effects run after the composition let it go),
    // forget its back stack, its injectors and its view models.
    val knownTabs = remember(tabsState) { mutableMapOf<String, DesktopTab>() }
    LaunchedEffect(tabs) {
        val ids = tabs.mapTo(mutableSetOf()) { it.id }
        knownTabs.values.filter { it.id !in ids }.forEach { closed ->
            tabStateHolder.removeState(closed.id)
            tabInjectors.remove(closed.id)
            closed.viewModelStore.clear()
        }
        knownTabs.clear()
        tabs.forEach { knownTabs[it.id] = it }
    }

    /** The active tab's controller, read when used: tabs change it. */
    val nav: () -> NavHostController? = { tabsState.active.navController }

    val actualSideMenuInjector = sideMenuInjector ?: remember {
        SideMenuKmpInjector()
    }

    val drawingInjection = remember { DrawingInjection() }

    val documentsGraphInjection =
        DocumentsGraphInjection(repositoryInjection = RepositoryInjector.singleton())

    val globalShellViewModel: GlobalShellViewModel =
        actualSideMenuInjector.provideSideMenuViewModel(keyboardEventFlow)
    val colorTheme by colorThemeOption.collectAsState()
    val accentColor by accentColorOption.collectAsState()
    val searchViewModel = KmpSearchInjection.singleton().provideViewModel()
    val showTabs = tabsStore != null

    // Middle click, Cmd/Ctrl + click or the right-click menu on a document or folder: a tab
    // behind the active one, like a browser.
    val openInNewTab: ((String, String, Boolean) -> Unit)? = if (showTabs) {
        { id, title, isFolder ->
            tabsState.open(
                if (isFolder) TabPlace.Folder(id) else TabPlace.Document(id, title),
                activate = false
            )
        }
    } else {
        null
    }
    val tabTitles = remember {
        TabTitles(
            documentRepository = { RepositoryInjector.singleton().provideDocumentRepository() },
            folderRepository = { FoldersInjector.singleton().provideFoldersRepository() },
        )
    }

    LaunchedEffect("initGlobalShellViewModel") {
        globalShellViewModel.init()
    }

    // Only the active tab tells the rest of the app where it is: the breadcrumb, the side menu,
    // the folder of a new document and Claude Code's current folder follow the tab on screen.
    // The flow gives the current entry first, so switching tabs updates them at once.
    val activeController = activeTab.navController
    LaunchedEffect(activeTab, activeController, globalShellViewModel) {
        NotesNavigationUseCase.singleton().setNoteNavigation(activeTab.notesNavigation)
        val controller = activeController ?: return@LaunchedEffect

        controller.currentBackStackEntryFlow.collect { navEntry ->
            globalShellViewModel.onDestinationChanged(navEntry.destination.route)
            val navigationType = navEntry.savedStateHandle.get<String?>(NAVIGATION_TYPE)
            val navigationPath = navEntry.savedStateHandle.get<String?>(NAVIGATION_PATH)
            if (navigationType != null && navigationPath != null) {
                val notesNavigation = NotesNavigation.fromType(
                    NotesNavigationType.fromType(navigationType),
                    navigationPath
                )
                activeTab.notesNavigation = notesNavigation
                NotesNavigationUseCase.singleton().setNoteNavigation(notesNavigation)
            }
            activeTab.route = navEntry.destination.route
            tabsState.placeChanged(activeTab, navEntry.tabPlace())
        }
    }

    // Claude Code asks for documents to write into: show them, so the person watches. Claude
    // works in a tab of its own, unless it writes into the document of the active tab: the
    // place the person was stays as it was. Showing a tab composes its editor, which attaches
    // last to the bridge, where LiveDocuments waits for it.
    LaunchedEffect(tabsState) {
        ClaudeCodeBridge.openRequests.collect { request ->
            when (request) {
                is ClaudeCodeBridge.OpenRequest.NewDocument -> {
                    // Without a folder, the folder the person is in (as navigateToNewNote did).
                    val folderId = request.folderId
                        ?: NotesNavigationUseCase.singleton().navigationState.value.id
                    tabsState.open(TabPlace.Other).pendingNavigation = { controller ->
                        controller.navigate("${Destinations.EDITOR.id}/$folderId")
                    }
                }

                is ClaudeCodeBridge.OpenRequest.Folder ->
                    tabsState.open(TabPlace.Folder(request.id))

                // The title is read from the document once it loads.
                is ClaudeCodeBridge.OpenRequest.Document -> {
                    val shown = (tabsState.active.place as? TabPlace.Document)?.id == request.id
                    if (!shown) {
                        val existing = tabsState.findDocument(request.id)
                        if (existing != null) {
                            tabsState.activate(existing.id)
                        } else {
                            tabsState.open(TabPlace.Document(request.id, ""))
                        }
                    }
                }
            }
        }
    }

    WriteopiaTheme(
        darkTheme = colorTheme.isDarkTheme(),
        accentColor = accentColor ?: AccentColor.DYNAMIC
    ) {
        val density = LocalDensity.current
        val globalBackground = WriteopiaTheme.colorScheme.globalBackground
        val isTextSelected by editorInjector.textSelectionActiveState.collectAsState()
        Box(modifier = Modifier.fillMaxSize()) {
            DragSelectionBox(modifier = modifier, isTextSelected = isTextSelected) {
                DraggableScreen {
                    Row(Modifier.background(globalBackground)) {
                        val sideMenuWidth by globalShellViewModel.showSideMenuState.collectAsState()

                        SideGlobalMenu(
                            modifier = Modifier.fillMaxHeight(),
                            foldersState = globalShellViewModel.sideMenuItems,
                            width = density.run { sideMenuWidth.toDp() },
                            homeClick = {
                                val controller = nav()
                                val navType = controller?.currentBackStackEntry
                                    ?.savedStateHandle
                                    ?.get<String>(NAVIGATION_TYPE)
                                    ?.let(NotesNavigationType::fromType)

                                if (navType != NotesNavigationType.ROOT) {
                                    controller?.navigateToNotes(NotesNavigation.Root)
                                }
                            },
                            favoritesClick = {
                                val controller = nav()
                                val navType = controller?.currentBackStackEntry
                                    ?.savedStateHandle
                                    ?.get<String?>(NAVIGATION_TYPE)
                                    ?.let(NotesNavigationType::fromType)

                                if (navType != NotesNavigationType.FAVORITES) {
                                    controller?.navigateToNotes(NotesNavigation.Favorites)
                                }
                            },
                            settingsClick = globalShellViewModel::showSettings,
                            addFolder = globalShellViewModel::addFolder,
                            editFolder = globalShellViewModel::editFolder,
                            navigateToFolder = { id ->
                                val navigation = NotesNavigation.Folder(id)
                                nav()?.navigateToNotes(navigation)
                            },
                            navigateToEditDocument = { id, title -> nav()?.navigateToNoteMobile(id, title) },
                            openInNewTab = openInNewTab,
                            moveRequest = globalShellViewModel::moveToFolder,
                            expandFolder = globalShellViewModel::expandFolder,
                            searchClick = globalShellViewModel::showSearch,
                            highlightContent = {},
                            changeIcon = globalShellViewModel::changeIcons,
                            toggleMaxScreen = toggleMaxScreen,
                            isSyncingState = globalShellViewModel.isSyncingState,
                            isLoadingContentState = globalShellViewModel.isLoadingContentState
                        )

                        Column {
                            if (hasGlobalHeader) {
                                GlobalHeader(
                                    nav,
                                    globalShellViewModel.folderPath,
                                    toggleMaxScreen
                                )
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // The tabs, under the bar of the back arrow, over the content.
                            if (showTabs) {
                                DesktopTabStrip(
                                    tabsState = tabsState,
                                    titles = tabTitles,
                                    onEmptySpaceDoubleClick = toggleMaxScreen,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                // Only the active tab is composed. Its back stack is kept by the
                                // holder (the controller is saveable) and its view models by the
                                // tab, so showing it again finds it as it was, scroll included.
                                // The editors of the other tabs are not on screen, so the one
                                // attached last to Claude Code is the one the person sees.
                                key(activeTab.id) {
                                    tabStateHolder.SaveableStateProvider(activeTab.id) {
                                        CompositionLocalProvider(
                                            LocalViewModelStoreOwner provides activeTab,
                                            LocalOpenInNewTab provides openInNewTab,
                                        ) {
                                            val tabController = rememberNavController()

                                            DisposableEffect(tabController) {
                                                activeTab.navController = tabController
                                                onDispose {}
                                            }

                                            Navigation(
                                                isDarkTheme = colorTheme.isDarkTheme(),
                                                startDestination = startDestination,
                                                notesMenuInjection = activeInjectors.notesMenu,
                                                sideMenuKmpInjector = actualSideMenuInjector,
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
                                                navController = tabController
                                            ) {}

                                            // A restored tab, or one opened elsewhere: go there once.
                                            LaunchedEffect(tabController) {
                                                activeTab.pendingPlace?.let { place ->
                                                    activeTab.pendingPlace = null
                                                    tabController.navigateToPlace(place)
                                                }
                                                activeTab.pendingNavigation?.let { navigate ->
                                                    activeTab.pendingNavigation = null
                                                    navigate(tabController)
                                                }
                                            }
                                        }
                                    }
                                }

                                val snackbarHostState = remember { SnackbarHostState() }

                                // The side menu and the notes screen share the folder controller,
                                // so this tells about the drops on both.
                                FolderMoveSnackbarEffect(
                                    snackbarHostState = snackbarHostState,
                                    lastMoveState = globalShellViewModel.lastMoveState,
                                    undoMove = globalShellViewModel::undoMove,
                                    dismissMove = globalShellViewModel::dismissMove
                                )

                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) { data ->
                                    WriteopiaSnackbar(snackbarData = data)
                                }

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
                                        localAiConfigController = globalShellViewModel,
                                        aiProviderState = globalShellViewModel.aiProviderState,
                                        aiProviderChoices = globalShellViewModel.aiProviderChoices,
                                        isCloudAiAvailable = globalShellViewModel.isCloudAiAvailable,
                                        selectAiProvider = globalShellViewModel::selectAiProvider,
                                        cloudAiUsageState = globalShellViewModel.cloudAiUsageState,
                                        userOnlineState = globalShellViewModel.userState,
                                        showDeleteConfirmation = globalShellViewModel.showDeleteConfirmation,
                                        workspaceToEdit = globalShellViewModel.workspaceToEdit,
                                        logoutInProgress = globalShellViewModel.logoutInProgress,
                                        deleteAccountInProgress =
                                            globalShellViewModel.deleteAccountInProgress,
                                        onDismissRequest = globalShellViewModel::hideSettings,
                                        selectColorTheme = selectColorTheme,
                                        selectAccentColor = selectAccentColor,
                                        workspaces = globalShellViewModel.availableWorkspaces,
                                        selectWorkplacePath = globalShellViewModel::changeWorkspaceLocalPath,
                                        loadCloudAiUsage = globalShellViewModel::loadCloudAiUsage,
                                        signIn = navigateToRegister,
                                        switchSpace = navigateToSpaceChoice,
                                        changeWorkspace = {
                                            globalShellViewModel.changeWorkspace(sideEffect = navigateToChooseWorkspace)
                                        },
                                        resetPassword = navigateToResetPassword,
                                        logout = {
                                            // Resets the nav graph back to START_APP so the login-state
                                            // check re-runs and can land on the space-choice screen.
                                            globalShellViewModel.logout(
                                                onSuccessSideEffect = navigateToChooseWorkspace
                                            )
                                        },
                                        showDeleteConfirm = globalShellViewModel::showDeleteConfirm,
                                        dismissDeleteConfirm = globalShellViewModel::dismissDeleteConfirm,
                                        deleteAccount = {
                                            globalShellViewModel.deleteAccount(
                                                sideEffect = navigateToAccountDeletionStarted
                                            )
                                        },
                                        addUserToTeam = globalShellViewModel::addUserToTeam,
                                        selectWorkspaceToManage =
                                            globalShellViewModel::selectWorkspaceToManage,
                                        usersInSelectedWorkspace = globalShellViewModel.usersOfWorkspaceToEdit,
                                        exportWorkspaceState = globalShellViewModel.exportWorkspaceState,
                                        onExportWorkspace = globalShellViewModel::exportWorkspace,
                                        onResetExportState = globalShellViewModel::resetExportState,
                                        userSearchQuery = globalShellViewModel.userSearchQuery,
                                        userSearchResults = globalShellViewModel.userSearchResults,
                                        onUserSearchQueryChange = globalShellViewModel::onUserSearchQueryChange,
                                        onUserSuggestionSelect = globalShellViewModel::onUserSuggestionSelect,
                                        onVersionEasterEgg = {
                                            globalShellViewModel.createVersionEasterEgg { id, title ->
                                                globalShellViewModel.hideSettings()
                                                nav()?.navigateToNoteMobile(id, title)
                                            }
                                        },
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
                                        documentClick = { id, title -> nav()?.navigateToNoteMobile(id, title) },
                                        onFolderClick = { navigation -> nav()?.navigateToFolder(navigation) },
                                        presentationClick = { documentId, presentationId, title ->
                                            nav()?.navigateToNoteWithPresentation(documentId, presentationId, title)
                                        },
                                        documentContentClick = { documentId, title, query ->
                                            nav()?.navigateToNoteWithSearch(documentId, title, query)
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

            // Claude Code at work: the banner with the button to stop it, over everything.
            ClaudeCodeAiModeBanner(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            )
        }
    }
}

private class TabInjectors(
    val editor: EditorKmpInjector,
    val notesMenu: NotesMenuInjection,
)

/**
 * The tabs of the signed-in workspace. With a store, they are loaded from it (and saved to it on
 * every change), which needs the workspace first: null until then. Without one, a single tab.
 * Leaving (signing out, changing workspace) clears the view models of every tab.
 */
@Composable
private fun rememberDesktopTabs(store: TabsStore?, host: DesktopTabsHost?): DesktopTabsState? {
    val state = if (store == null) {
        remember { DesktopTabsState() }
    } else {
        produceState<DesktopTabsState?>(null, store) {
            val workspaceId = (
                AuthCoreInjectionNeo.singleton().provideAuthRepository().getWorkspace()
                    ?: Workspace.disconnectedWorkspace()
            ).id
            value = DesktopTabsState(
                initial = store.load(workspaceId),
                onChange = { saved -> store.save(workspaceId, saved) }
            )
        }.value
    }

    DisposableEffect(state, host) {
        host?.state = state
        onDispose {
            if (host?.state === state) host?.state = null
            state?.clear()
        }
    }

    return state
}

fun startDestination() =
    "${Destinations.CHOOSE_NOTE.id}/${NotesNavigationType.ROOT.type}/path"
