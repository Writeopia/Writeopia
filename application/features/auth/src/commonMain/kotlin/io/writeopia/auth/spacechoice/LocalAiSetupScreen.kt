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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.controller.LocalAiConfigController
import io.writeopia.localaiconfig.ui.LocalAiConfigScreen
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme

/**
 * Shown right after choosing the offline space (desktop only - see [io.writeopia.auth.navigation.startScreen]
 * usage), so the user can configure local AI before landing in the app rather than discovering
 * the option buried in Settings later.
 */
@Composable
fun LocalAiSetupScreen(
    controller: LocalAiConfigController,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = WrStrings.localAiSetupTitle(),
                color = WriteopiaTheme.colorScheme.textLight,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = WrStrings.localAiSetupDescription(),
                color = WriteopiaTheme.colorScheme.textLighter,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Once the model picked in the wizard is downloading, go to the app: the download
            // goes on there as an AI task, with its progress next to the other AI tasks.
            LocalAiConfigScreen(controller = controller, onDownloadStarted = onContinueClick)

            Spacer(modifier = Modifier.height(32.dp))

            CommonButton(
                text = WrStrings.continueToApp(),
                modifier = Modifier.fillMaxWidth(),
                clickListener = onContinueClick
            )
        }
    }
}
