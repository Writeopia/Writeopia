package io.writeopia.notemenu.extensions

import io.writeopia.models.Folder
import io.writeopia.notemenu.ui.dto.MenuItemUi
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.preview.PreviewParser

fun MenuItem.toUiCard(
    previewParser: PreviewParser? = null,
    selected: Boolean = false,
    limit: Int = 0,
    expanded: Boolean = false,
    highlighted: Boolean = false
): MenuItemUi =
    when (this) {
        is Folder -> {
            MenuItemUi.FolderUi(
                documentId = id,
                title = title,
                selected = selected,
                isFavorite = favorite,
                itemsCount = itemCount,
                expanded = expanded,
                parentId = parentId,
                highlighted = highlighted,
                icon = icon
            )
        }

        is Document -> {
            MenuItemUi.DocumentUi(
                documentId = id,
                title = title,
                lastEdit = "",
                preview = content.values.let { previewParser?.preview(it, limit) ?: emptyList() },
                selected = selected,
                parentId = parentId,
                isFavorite = favorite,
                highlighted = highlighted,
                icon = icon
            )
        }

        else -> throw IllegalArgumentException("MenuItemUi could not me created")
    }
