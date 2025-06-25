package io.writeopia.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.writeopia.account.ui.DownloadModels
import io.writeopia.common.utils.download.DownloadState
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrImages
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun OnboardingWorkspace(
    showOnboard: OnboardingState,
    downloadModelState: StateFlow<ResultData<DownloadState>>,
    downloadModel: (String) -> Unit,
    onCloseClick: () -> Unit,
    onClosePermanentlyClick: () -> Unit,
) {
    Crossfade(showOnboard) { state ->
        Box(modifier = Modifier.size(width = 400.dp, height = 350.dp)) {
            when (state) {
                OnboardingState.CONFIGURATION -> {
                    Configuration(downloadModelState, downloadModel)
                }

                OnboardingState.CONGRATULATION -> {
                    ConfigurationComplete()
                }

                OnboardingState.HIDDEN, OnboardingState.COMPLETE -> {}
            }

            Icon(
                imageVector = WrIcons.close,
                contentDescription = WrStrings.close(),
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(end = 8.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCloseClick)
                    .padding(6.dp)
            )

            Text(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onClosePermanentlyClick)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.medium
                    )
                    .padding(8.dp)
                    .align(Alignment.BottomEnd),
                text = "Don't show again",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Configuration(
    downloadModelState: StateFlow<ResultData<DownloadState>>,
    downloadModel: (String) -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = WrStrings.onboardingHello(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = WrStrings.onboardingTutorialExplain(),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        DownloadOllamaStep()

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = WrStrings.onboardingChooseAi(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1F)) {
                DownloadModels(
                    downloadModelState = downloadModelState,
                    downloadModel = downloadModel,
                )
            }

            Box(
                modifier = Modifier.padding(8.dp)
                    .background(Color.Blue, CircleShape)
                    .size(32.dp)
            ) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = "2",
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun DownloadOllamaStep() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1F)) {
            Text(
                text = WrStrings.downloadOllama(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            val uriHandler = LocalUriHandler.current

            Text(
                text = WrStrings.accessOllamaSite(),
                style = MaterialTheme.typography.bodySmall,
                color = WriteopiaTheme.colorScheme.textLight,
                fontSize = 12.sp,
                modifier = Modifier.clickable {
                    uriHandler.openUri(uri = "https://ollama.com")
                }.pointerHoverIcon(icon = PointerIcon.Hand)
            )
        }

        Box(
            modifier = Modifier.padding(8.dp)
                .background(Color.Blue, CircleShape)
                .size(32.dp)
        ) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = "1",
                textAlign = TextAlign.Center,
                color = Color.White
            )
        }
    }
}

@Composable
private fun BoxScope.ConfigurationComplete(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        WrImages.onboardingRobot()?.let { image ->
            Image(
                painter = image,
                contentDescription = WrStrings.smallRobot(),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = WrStrings.ollamaConfigComplete(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = WrStrings.privateAiEnabled(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = WriteopiaTheme.colorScheme.textLighter
        )
    }
}
