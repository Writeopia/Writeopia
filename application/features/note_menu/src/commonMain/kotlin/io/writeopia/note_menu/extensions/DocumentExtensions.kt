package io.writeopia.note_menu.extensions

import io.writeopia.note_menu.ui.dto.DocumentUi
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.preview.PreviewParser

internal fun Document.toUiCard(
    previewParser: PreviewParser,
    selected: Boolean = false
): DocumentUi =
    DocumentUi(
        documentId = id,
        title = title,
        lastEdit = "",
        preview = content.values.let(previewParser::preview),
        selected = selected
    )