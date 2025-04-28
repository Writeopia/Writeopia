package io.writeopia.documents.graph.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.common.utils.Destinations
import io.writeopia.documents.graph.di.DocumentsGraphInjection
import io.writeopia.forcegraph.ForceDirectedGraph
import io.writeopia.sdk.models.document.MenuItem

fun NavController.navigateToForceGraph() {
    navigate(Destinations.FORCE_GRAPH.id)
}

fun graphForce() = Destinations.FORCE_GRAPH.id

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.documentsGraphNavigation(
    navigationController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    documentsGraphInjection: DocumentsGraphInjection
) {
    composable(
        route = graphForce(),
    ) {
        val viewModel = documentsGraphInjection.injectViewModel()
        val state by viewModel.graphState.collectAsState()

        ForceDirectedGraph<MenuItem>(
            modifier = Modifier.fillMaxSize(),
            nodes = state.nodes,
            links = state.links
        )
    }
}
