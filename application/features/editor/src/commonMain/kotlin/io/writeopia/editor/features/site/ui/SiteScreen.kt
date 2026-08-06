package io.writeopia.editor.features.site.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.editor.configuration.ui.DrawConfigFactory
import io.writeopia.editor.features.site.viewmodel.SiteUiState
import io.writeopia.editor.features.site.viewmodel.SiteViewModel
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.ui.WriteopiaEditor
import io.writeopia.ui.drawer.factory.DrawersFactory
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawState
import io.writeopia.ui.model.DrawStory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun SiteScreen(
    documentId: String,
    isDarkTheme: Boolean,
    siteViewModel: SiteViewModel,
    drawersFactory: DrawersFactory,
    modifier: Modifier = Modifier
) {
    val uiState by siteViewModel.uiState.collectAsState()

    LaunchedEffect(documentId) {
        siteViewModel.loadDocument(documentId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            is SiteUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is SiteUiState.Success -> {
                SiteContent(
                    document = state.document,
                    isDarkTheme = isDarkTheme,
                    drawersFactory = drawersFactory,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            is SiteUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error loading document",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "The document could not be loaded. It may not exist or is not published.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            is SiteUiState.NotFound -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Document not found",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "This document does not exist or is not publicly available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteContent(
    document: Document,
    isDarkTheme: Boolean,
    drawersFactory: DrawersFactory,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val drawConfig = remember { DrawConfigFactory.getDrawConfig() }

    // Create a minimal read-only WriteopiaStateManager
    val readOnlyManager = remember(document) {
        val writeopiaManager = WriteopiaManager()
        WriteopiaStateManager.create(
            writeopiaManager = writeopiaManager,
            dispatcher = Dispatchers.Default,
            selectionState = MutableStateFlow(false),
            keyboardEventFlow = MutableStateFlow(null)
        ).apply {
            loadDocument(document)
        }
    }

    val storyState by readOnlyManager.toDraw.collectAsState(initial = DrawState())

    val drawers = drawersFactory.create(
        manager = readOnlyManager,
        editable = false,
        isDarkTheme = isDarkTheme,
        drawConfig = drawConfig
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Document title
        val title = document.title.ifBlank { "Untitled" }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Document content
        WriteopiaEditor(
            modifier = Modifier.widthIn(max = 850.dp),
            editable = false,
            listState = listState,
            drawers = drawers,
            storyState = storyState
        )
    }
}
