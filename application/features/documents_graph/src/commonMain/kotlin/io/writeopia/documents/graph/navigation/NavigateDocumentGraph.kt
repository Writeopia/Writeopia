package io.writeopia.documents.graph.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.common.utils.Destinations
import io.writeopia.documents.graph.di.DocumentsGraphInjection
import io.writeopia.documents.graph.extensions.toGraph
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
        val state by viewModel.graphState.collectAsState()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val graph by derivedStateOf {
                state.toGraph(
                    this.maxWidth.value,
                    this.maxHeight.value
                )
            }

            ForceDirectedGraph(
                nodes = graph.nodes,
                links = graph.links
            )
        }
    }
}
