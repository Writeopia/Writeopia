package io.writeopia.documents.graph.extensions

import io.writeopia.forcegraph.model.Link
import io.writeopia.forcegraph.model.Node
import io.writeopia.sdk.models.document.MenuItem

internal fun Map<String, List<MenuItem>>.toGraph() {
    this.values.flatten().map { item ->
        Node()
    }
}
