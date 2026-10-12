package io.writeopia.documents.graph.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.documents.graph.GraphGrouping
import io.writeopia.resources.WrStrings
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.common.utils.Destinations
import io.writeopia.documents.graph.di.DocumentsGraphInjection
import io.writeopia.documents.graph.ui.GraphItemCard
import io.writeopia.forcegraph.ForceDirectedGraph
import io.writeopia.forcegraph.GraphViewState
import io.writeopia.forcegraph.rememberGraphViewState

fun NavController.navigateToForceGraph() {
    navigate(Destinations.FORCE_GRAPH.id)
}

fun graphForce() = Destinations.FORCE_GRAPH.id

fun NavGraphBuilder.documentsGraphNavigation(
    documentsGraphInjection: DocumentsGraphInjection,
    onOpenDocument: (id: String, title: String) -> Unit = { _, _ -> },
    onOpenFolder: (id: String) -> Unit = {},
    onOpenTag: (id: String) -> Unit = {},
) {
    composable(
        route = graphForce(),
    ) {
        val viewModel = documentsGraphInjection.injectViewModel()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // The canvas draws in pixels: the layout size in dp would centre the graph in the top
            // left quarter of a high-density screen.
            val density = LocalDensity.current
            viewModel.initSize(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() })
            val state by viewModel.graphSelectedState.collectAsState()

            val viewState = rememberGraphViewState()

            ForceDirectedGraph(
                modifier = Modifier.fillMaxSize(),
                nodes = state.nodes,
                links = state.links,
                onNodeSelected = viewModel::selectNode,
                viewState = viewState,
            )

            ZoomButtons(
                viewState = viewState,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            )

            val selectedItem by viewModel.selectedItem.collectAsState()
            selectedItem?.let { details ->
                GraphItemCard(
                    details = details,
                    onOpenDocument = onOpenDocument,
                    onOpenFolder = onOpenFolder,
                    onOpenTag = onOpenTag,
                    onClose = viewModel::closeDetails,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                )
            }

            val grouping by viewModel.grouping.collectAsState()
            GroupingSelector(
                grouping = grouping,
                onGroupingChange = viewModel::setGrouping,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            )
        }
    }
}

/** Folders or tags as the hubs of the graph. */
@Composable
private fun GroupingSelector(
    grouping: GraphGrouping,
    onGroupingChange: (GraphGrouping) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = GraphGrouping.entries

    // Compact and only slightly rounded: a small control in the corner, not a toolbar.
    SingleChoiceSegmentedButtonRow(modifier = modifier.height(26.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == grouping,
                onClick = { onGroupingChange(option) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                    baseShape = RoundedCornerShape(6.dp)
                ),
                icon = {},
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.testTag("$GRAPH_GROUPING_TAG.${option.name}")
            ) {
                Text(
                    text = when (option) {
                        GraphGrouping.FOLDERS -> WrStrings.graphGroupingFolders()
                        GraphGrouping.TAGS -> WrStrings.documentTagsTitle()
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/** Zoom in, zoom out and back to 1:1. The only way to zoom on a desktop without a mouse wheel. */
@Composable
private fun ZoomButtons(viewState: GraphViewState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ZoomButton(symbol = "+", testTag = "$GRAPH_ZOOM_TAG.in", onClick = viewState::zoomIn)
        Spacer(modifier = Modifier.height(4.dp))
        ZoomButton(symbol = "−", testTag = "$GRAPH_ZOOM_TAG.out", onClick = viewState::zoomOut)
        Spacer(modifier = Modifier.height(4.dp))
        ZoomButton(symbol = "1:1", testTag = "$GRAPH_ZOOM_TAG.reset", onClick = viewState::reset)
    }
}

@Composable
private fun ZoomButton(symbol: String, testTag: String, onClick: () -> Unit) {
    Text(
        text = symbol,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .width(32.dp)
            .padding(vertical = 5.dp)
            .testTag(testTag),
        textAlign = TextAlign.Center
    )
}

const val GRAPH_GROUPING_TAG = "GraphGrouping"
const val GRAPH_ZOOM_TAG = "GraphZoom"
