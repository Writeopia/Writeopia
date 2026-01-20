package io.writeopia.ui.drawer.factory

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawConfig

interface DrawersFactory {

    @Composable
    fun create(
        manager: WriteopiaStateManager,
        editable: Boolean = true,
        aiExplanation: String = "AI Generated",
        isDarkTheme: Boolean = false,
        onHeaderClick: () -> Unit = {},
        drawConfig: DrawConfig = DrawConfig(),
        fontFamily: FontFamily? = null,
        generateSection: (Int) -> Unit = {},
        receiveExternalFile: (List<ExternalFile>, Int) -> Unit = { _, _ -> },
        onDocumentLinkClick: (String) -> Unit = {},
        linkLeadingIcon: ImageVector? = null,
        equationToImageUrl: String? = null
    ): Map<Int, StoryStepDrawer>
}
