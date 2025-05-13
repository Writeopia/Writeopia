package io.writeopia.documents.graph.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.common.utils.Destinations
import io.writeopia.documents.graph.di.DocumentsGraphInjection
import io.writeopia.forcegraph.ForceDirectedGraph

fun NavController.navigateToForceGraph() {
    navigate(Destinations.FORCE_GRAPH.id)
}

fun graphForce() = Destinations.FORCE_GRAPH.id

fun NavGraphBuilder.documentsGraphNavigation(
    documentsGraphInjection: DocumentsGraphInjection
) {
    composable(
        route = graphForce(),
    ) {
        val viewModel = documentsGraphInjection.injectViewModel()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            viewModel.initSize(maxWidth.value, maxHeight.value)
            val state by viewModel.graphSelectedState.collectAsState()

            ForceDirectedGraph(
                modifier = Modifier.fillMaxSize().background(Color.LightGray),
                nodes = state.nodes,
                links = state.links,
                onNodeSelected = viewModel::selectNode
            )
        }
    }
}
