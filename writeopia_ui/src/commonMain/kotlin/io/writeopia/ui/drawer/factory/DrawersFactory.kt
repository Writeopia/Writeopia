package io.writeopia.ui.drawer.factory

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawConfig

interface DrawersFactory {

    @Composable
    fun create(
        manager: WriteopiaStateManager,
        defaultBorder: Shape,
        editable: Boolean,
        groupsBackgroundColor: Color,
        onHeaderClick: () -> Unit,
        drawConfig: DrawConfig,
        fontFamily: FontFamily?,
        generateSection: (Int) -> Unit,
        receiveExternalFile: (List<ExternalFile>, Int) -> Unit,
        onDocumentLinkClick: (String) -> Unit,
    ): Map<Int, StoryStepDrawer>
}
