package io.writeopia.auth.spacechoice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.commonui.workplace.WorkspacePathSelector
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * Shown after [LocalAiSetupScreen] (desktop only), so the user can pick the folder in the computer
 * where the private workspace is kept, instead of finding the option in Settings later.
 */
@Composable
fun LocalFolderSetupScreen(
    workplacePathState: StateFlow<String>,
    selectWorkplacePath: (String) -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val workplacePath by workplacePathState.collectAsState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WriteopiaTheme.colorScheme.globalBackground)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        // Centered in the window; scrolls once the content is taller than it.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 700.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = WrStrings.localFolderSetupTitle(),
                color = WriteopiaTheme.colorScheme.textLight,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = WrStrings.localFolderSetupDescription(),
                color = WriteopiaTheme.colorScheme.textLighter,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = WrStrings.localFolder(),
                color = WriteopiaTheme.colorScheme.textLight,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            WorkspacePathSelector(
                workplacePath = workplacePath,
                selectWorkplacePath = selectWorkplacePath
            )

            Spacer(modifier = Modifier.height(32.dp))

            CommonButton(
                text = WrStrings.continueToApp(),
                modifier = Modifier.fillMaxWidth(),
                clickListener = onContinueClick
            )
        }
    }
}
