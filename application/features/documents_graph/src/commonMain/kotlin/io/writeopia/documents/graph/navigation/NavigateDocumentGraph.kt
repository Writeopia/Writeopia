package io.writeopia.documents.graph.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.common.utils.Destinations
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
) {
    composable(
        route = graphForce(),
    ) {
        ForceDirectedGraph<MenuItem>(
            modifier = Modifier.fillMaxSize(),
            nodes = emptyList(),
            links = emptyList()
        )
    }
}
