package io.writeopia.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.writeopia.account.di.AccountMenuKmpInjector
import io.writeopia.account.ui.AccountMenuScreen
import io.writeopia.common.utils.AccountRoute
import io.writeopia.common.utils.AuthMenuInnerNavigationRoute
import io.writeopia.common.utils.AuthResetPasswordRoute
import io.writeopia.common.utils.ChooseNoteRoute
import io.writeopia.common.utils.DrawingRoute
import io.writeopia.common.utils.EditorRoute
import io.writeopia.common.utils.ForceGraphRoute
import io.writeopia.common.utils.MainAppRoute
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.NotesNavigationType
import io.writeopia.common.utils.NotificationsRoute
import io.writeopia.common.utils.PresentationRoute
import io.writeopia.common.utils.Route
import io.writeopia.common.utils.SearchRoute
import io.writeopia.documents.graph.di.DocumentsGraphInjection
import io.writeopia.drawing.di.DrawingInjection
import io.writeopia.drawing.ui.screen.DrawingScreen
import io.writeopia.editor.di.TextEditorInjector
import io.writeopia.editor.features.editor.copy.CopyManager
import io.writeopia.editor.features.editor.ui.screen.TextEditorScreen
import io.writeopia.editor.features.presentation.ui.PresentationScreen
import io.writeopia.features.notifications.NotificationsScreen
import io.writeopia.features.search.DocumentsSearchScreen
import io.writeopia.features.search.di.SearchInjection
import io.writeopia.forcegraph.ForceDirectedGraph
import io.writeopia.global.shell.di.SideMenuKmpInjector
import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.notemenu.di.NotesMenuInjection
import io.writeopia.notemenu.ui.screen.menu.NotesMenuScreenContent
import io.writeopia.sdk.models.drawing.DrawingData
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * Creates a NavBackStack for use with Navigation 3.
 * Call this at the top level of your composable hierarchy to manage navigation state externally.
 */
@Composable
fun rememberWriteopiaNavBackStack(startDestination: Route): NavBackStack<NavKey> {
    return rememberNavBackStack(navigationConfig, startDestination)
}

/**
 * Main Navigation composable using Navigation 3.
 *
 * @param startDestination The initial route to display
 * @param extraEntries Lambda to provide additional navigation entries (e.g., auth flow)
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Navigation(
    isDarkTheme: Boolean,
    startDestination: Route,
    notesMenuInjection: NotesMenuInjection,
    documentsGraphInjection: DocumentsGraphInjection? = null,
    sideMenuKmpInjector: SideMenuKmpInjector? = null,
    editorInjector: TextEditorInjector,
    drawingInjection: DrawingInjection? = null,
    searchInjection: SearchInjection? = null,
    selectedColorTheme: StateFlow<ColorThemeOption?>,
    selectedAccentColor: StateFlow<AccentColor?>,
    selectColorTheme: (ColorThemeOption) -> Unit,
    selectAccentColor: (AccentColor) -> Unit,
    onDrawingSaved: (String, String, DrawingData) -> Unit = { _, _, _ -> },
    nestedScrollConnection: NestedScrollConnection? = null,
    isToolbarVisible: Boolean = true,
    navigationBar: @Composable () -> Unit,
    externalBackStack: NavBackStack<NavKey>? = null,
    extraEntries: @Composable (
        backStack: NavBackStack<NavKey>,
        sharedTransitionScope: SharedTransitionScope
    ) -> Unit = { _, _ -> }
) {
    val internalBackStack = rememberNavBackStack(navigationConfig, startDestination)
    val backStack = externalBackStack ?: internalBackStack

    SharedTransitionLayout {
        val sharedTransitionScope = this@SharedTransitionLayout

        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                // Main app / Notes menu (root)
                entry<MainAppRoute> {
                    val notesNavigation = NotesNavigation.Root
                    val chooseNoteViewModel = notesMenuInjection.provideChooseNoteViewModel(
                        notesNavigation = notesNavigation
                    )
                    val ollamaConfigController = sideMenuKmpInjector?.provideOllamaConfigController()

                    NotesMenuScreenContent(
                        isDarkTheme = isDarkTheme,
                        folderId = notesNavigation.id,
                        chooseNoteViewModel = chooseNoteViewModel,
                        ollamaConfigController = ollamaConfigController,
                        sharedTransitionScope = sharedTransitionScope,
                        onNewNoteClick = {
                            backStack.add(EditorRoute(parentFolderId = notesNavigation.id))
                        },
                        onNoteClick = { id, title ->
                            backStack.add(EditorRoute(noteId = id, noteTitle = title))
                        },
                        onAccountClick = { backStack.add(AccountRoute) },
                        selectColorTheme = selectColorTheme,
                        navigateToFolders = { nav ->
                            backStack.add(
                                ChooseNoteRoute(
                                    navigationType = nav.navigationType.type,
                                    navigationPath = if (nav is NotesNavigation.Folder) nav.id else ""
                                )
                            )
                        },
                        onForceGraphSelected = { backStack.add(ForceGraphRoute) },
                        addFolder = chooseNoteViewModel::addFolder,
                        editFolder = chooseNoteViewModel::editFolder,
                        nestedScrollConnection = nestedScrollConnection,
                        isToolbarVisible = isToolbarVisible,
                        navigationBar = navigationBar,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background)
                    )
                }

                // Choose note / folder navigation
                entry<ChooseNoteRoute> { route ->
                    val notesNavigation = NotesNavigation.fromType(
                        NotesNavigationType.fromType(route.navigationType),
                        route.navigationPath
                    )
                    val chooseNoteViewModel = notesMenuInjection.provideChooseNoteViewModel(
                        notesNavigation = notesNavigation
                    )
                    val ollamaConfigController = sideMenuKmpInjector?.provideOllamaConfigController()

                    NotesMenuScreenContent(
                        isDarkTheme = isDarkTheme,
                        folderId = notesNavigation.id,
                        chooseNoteViewModel = chooseNoteViewModel,
                        ollamaConfigController = ollamaConfigController,
                        sharedTransitionScope = sharedTransitionScope,
                        onNewNoteClick = {
                            backStack.add(EditorRoute(parentFolderId = notesNavigation.id))
                        },
                        onNoteClick = { id, title ->
                            backStack.add(EditorRoute(noteId = id, noteTitle = title))
                        },
                        onAccountClick = { backStack.add(AccountRoute) },
                        selectColorTheme = selectColorTheme,
                        navigateToFolders = { nav ->
                            backStack.add(
                                ChooseNoteRoute(
                                    navigationType = nav.navigationType.type,
                                    navigationPath = if (nav is NotesNavigation.Folder) nav.id else ""
                                )
                            )
                        },
                        onForceGraphSelected = { backStack.add(ForceGraphRoute) },
                        addFolder = chooseNoteViewModel::addFolder,
                        editFolder = chooseNoteViewModel::editFolder,
                        nestedScrollConnection = nestedScrollConnection,
                        isToolbarVisible = isToolbarVisible,
                        navigationBar = navigationBar,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background)
                    )
                }

                // Editor
                entry<EditorRoute> { route ->
                    val noteDetailsViewModel = editorInjector.provideNoteDetailsViewModel(
                        route.parentFolderId,
                        copyManager = CopyManager(LocalClipboardManager.current)
                    ).apply {
                        setTheme(isDarkTheme)
                    }

                    val documentId = noteDetailsViewModel.writeopiaManager.documentInfo.value.id

                    TextEditorScreen(
                        documentId = route.noteId?.takeIf { it != "null" },
                        title = route.noteTitle?.takeIf { it != "null" },
                        isDarkTheme = isDarkTheme,
                        noteEditorViewModel = noteDetailsViewModel,
                        playPresentation = {
                            val docId = route.noteId ?: documentId
                            backStack.add(PresentationRoute(docId))
                        },
                        navigateBack = { backStack.removeLastOrNull() },
                        onDocumentLinkClick = { id ->
                            backStack.add(EditorRoute(noteId = id, noteTitle = ""))
                        },
                        onNewDrawingClick = {
                            val docId = route.noteId ?: documentId
                            backStack.add(DrawingRoute(documentId = docId))
                        },
                        onDrawingClick = { storyStep, _ ->
                            val docId = route.noteId ?: documentId
                            backStack.add(
                                DrawingRoute(
                                    documentId = docId,
                                    storyStepId = storyStep.id,
                                    drawingJson = storyStep.text
                                )
                            )
                        },
                        nestedScrollConnection = nestedScrollConnection,
                        isToolbarVisible = isToolbarVisible,
                        modifier = Modifier.background(
                            WriteopiaTheme.colorScheme.cardBg,
                            MaterialTheme.shapes.large
                        )
                    )
                }

                // Presentation
                entry<PresentationRoute> { route ->
                    val viewModel = editorInjector.providePresentationViewModel()
                    viewModel.loadDocument(route.documentId)
                    PresentationScreen(viewModel)
                }

                // Drawing
                entry<DrawingRoute> { route ->
                    if (drawingInjection != null) {
                        val drawingViewModel = viewModel { drawingInjection.provideDrawingViewModel() }
                        val initialJson = route.drawingJson?.decodeDrawingJson()

                        DrawingScreen(
                            viewModel = drawingViewModel,
                            initialDrawingJson = initialJson,
                            onSave = { drawingData ->
                                onDrawingSaved(route.documentId, route.storyStepId ?: "", drawingData)
                                backStack.removeLastOrNull()
                            },
                            onCancel = { backStack.removeLastOrNull() }
                        )
                    }
                }

                // Force Graph
                entry<ForceGraphRoute> {
                    if (documentsGraphInjection != null) {
                        val viewModel = documentsGraphInjection.injectViewModel()

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            viewModel.initSize(maxWidth.value, maxHeight.value)
                            val state by viewModel.graphSelectedState.collectAsState()

                            ForceDirectedGraph(
                                modifier = Modifier.fillMaxSize(),
                                nodes = state.nodes,
                                links = state.links,
                                onNodeSelected = viewModel::selectNode
                            )
                        }
                    }
                }

                // Account
                entry<AccountRoute> {
                    val accountMenuViewModel = AccountMenuKmpInjector.singleton().provideAccountMenuViewModel()

                    AccountMenuScreen(
                        modifier = Modifier.background(WriteopiaTheme.colorScheme.lightBackground),
                        accountMenuViewModel = accountMenuViewModel,
                        isLoggedInState = accountMenuViewModel.isLoggedIn,
                        selectedColorTheme = selectedColorTheme,
                        selectedAccentColor = selectedAccentColor,
                        onLogout = { backStack.add(AuthMenuInnerNavigationRoute) },
                        goToRegister = { backStack.add(AuthMenuInnerNavigationRoute) },
                        changeAccount = { backStack.add(AuthMenuInnerNavigationRoute) },
                        resetPassword = { backStack.add(AuthResetPasswordRoute) },
                        selectColorTheme = selectColorTheme,
                        selectAccentColor = selectAccentColor
                    )
                }

                // Search
                entry<SearchRoute> {
                    if (searchInjection != null) {
                        val searchViewModel = viewModel { searchInjection.provideViewModel() }

                        DocumentsSearchScreen(
                            modifier = Modifier,
                            searchState = searchViewModel.searchState,
                            searchResults = searchViewModel.queryResults,
                            onSearchType = searchViewModel::onSearchType,
                            documentClick = { id, title ->
                                backStack.add(EditorRoute(noteId = id, noteTitle = title))
                            },
                            onFolderClick = { nav ->
                                backStack.add(
                                    ChooseNoteRoute(
                                        navigationType = nav.navigationType.type,
                                        navigationPath = if (nav is NotesNavigation.Folder) nav.id else ""
                                    )
                                )
                            }
                        )
                    }
                }

                // Notifications
                entry<NotificationsRoute> {
                    NotificationsScreen(
                        navigationClick = { backStack.removeLastOrNull() },
                        nestedScrollConnection = nestedScrollConnection,
                        isToolbarVisible = isToolbarVisible,
                        bottomBar = navigationBar
                    )
                }
            }
        )

        // Allow additional entries to be provided (for auth, startup, etc.)
        extraEntries(backStack, sharedTransitionScope)
    }
}

/**
 * Decode drawing JSON from URL navigation.
 */
private fun String.decodeDrawingJson(): String {
    return this
        .replace("%7D", "}")
        .replace("%7B", "{")
        .replace("%5D", "]")
        .replace("%5B", "[")
        .replace("%3F", "?")
        .replace("%3D", "=")
        .replace("%3A", ":")
        .replace("%2F", "/")
        .replace("%2B", "+")
        .replace("%27", "'")
        .replace("%26", "&")
        .replace("%23", "#")
        .replace("%22", "\"")
        .replace("%20", " ")
        .replace("%25", "%")
}
