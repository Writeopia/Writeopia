package io.writeopia.auth.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

/** The download page of the desktop and mobile apps. */
const val APPS_DOWNLOAD_URL = "https://writeopia.io/download"

/**
 * Asks a user who signed in but is not premium to subscribe. The free alternative is the offline
 * space; on the web, which has none, the native apps are recommended instead.
 */
@Composable
fun PaywallScreen(
    stateFlow: StateFlow<PaywallState>,
    isWeb: Boolean,
    onPremium: () -> Unit,
    onCheckAgain: () -> Unit,
    onUseOffline: () -> Unit,
    onOtherAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by stateFlow.collectAsState()

    LaunchedEffect(state.premium) {
        if (state.premium) onPremium()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (state.premium || (state.checking && state.monthlyPrice == null && !state.failed)) {
            // Nothing to offer until the first answer: a premium user only sees this.
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            PaywallContent(state, isWeb, onCheckAgain, onUseOffline, onOtherAccount)
        }
    }
}

@Composable
private fun PaywallContent(
    state: PaywallState,
    isWeb: Boolean,
    onCheckAgain: () -> Unit,
    onUseOffline: () -> Unit,
    onOtherAccount: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large

    Column(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 80.dp, bottom = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            WrStrings.paywallTitle(),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            WrStrings.paywallMessage(),
            color = WriteopiaTheme.colorScheme.textLighter,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        state.monthlyPrice?.let { price ->
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                WrStrings.paywallPrice(price),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        CheckStatus(state)

        TextButton(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = shape)
                .fillMaxWidth(),
            onClick = onCheckAgain,
            enabled = !state.checking
        ) {
            if (state.checking) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(
                text = if (state.failed) WrStrings.retry() else WrStrings.paywallCheckAgain(),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        FreeAlternative(isWeb = isWeb, onUseOffline = onUseOffline)

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onOtherAccount) {
            Text(WrStrings.paywallOtherAccount(), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CheckStatus(state: PaywallState) {
    val message = when {
        state.checking -> WrStrings.paywallChecking()
        state.failed -> WrStrings.paywallError()
        state.stillNotPremium -> WrStrings.paywallStillNotPremium()
        else -> null
    } ?: return

    Text(
        message,
        color = if (state.failed && !state.checking) {
            MaterialTheme.colorScheme.error
        } else {
            WriteopiaTheme.colorScheme.textLighter
        },
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun FreeAlternative(isWeb: Boolean, onUseOffline: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            WrStrings.paywallFreeTitle(),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            if (isWeb) WrStrings.paywallFreeWeb() else WrStrings.paywallFreeOffline(),
            color = WriteopiaTheme.colorScheme.textLighter,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        TextButton(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.primary, shape)
                .fillMaxWidth(),
            onClick = {
                if (isWeb) uriHandler.openUri(APPS_DOWNLOAD_URL) else onUseOffline()
            }
        ) {
            Text(
                if (isWeb) WrStrings.paywallDownloadApps() else WrStrings.paywallUseOffline(),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
