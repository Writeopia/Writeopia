package io.writeopia.account.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings

/** The "Workspace" page of the phone settings: what the workspace keeps, each on its own page. */
@Composable
fun SettingsWorkspaceScreen(
    navigateToHeaderImages: () -> Unit,
    navigateToTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        WorkspaceContentLinks(navigateToHeaderImages = navigateToHeaderImages, navigateToTags = navigateToTags)
    }
}

/** The links to the header pictures and to the tags of the workspace. */
@Composable
fun WorkspaceContentLinks(
    navigateToHeaderImages: () -> Unit,
    navigateToTags: () -> Unit,
) {
    Column {
        SettingsMenuItem(
            title = WrStrings.settingsHeaderImages(),
            icon = WrIcons.image,
            onClick = navigateToHeaderImages,
            modifier = Modifier.testTag("SettingsWorkspace.headerImages")
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsMenuItem(
            title = WrStrings.settingsTags(),
            icon = WrIcons.tag,
            onClick = navigateToTags,
            modifier = Modifier.testTag("SettingsWorkspace.tags")
        )
    }
}

/** A page of the phone settings that scrolls, around [SettingsHeaderImagesRoute] or [SettingsTagsRoute]. */
@Composable
fun ScrollingSettingsPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        content()
    }
}
