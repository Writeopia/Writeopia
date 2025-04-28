package io.writeopia.documents.graph

import io.writeopia.sdk.models.utils.Traversable

data class ItemData(
    override val id: String,
    val title: String,
    override val parentId: String,
    val isFolder: Boolean
) : Traversable
