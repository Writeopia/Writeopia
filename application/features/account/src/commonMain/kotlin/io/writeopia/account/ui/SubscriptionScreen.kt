package io.writeopia.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.account.viewmodel.SubscriptionViewModel
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.network.subscription.SubscriptionResponse
import io.writeopia.theme.WriteopiaTheme

@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val subscriptionState by viewModel.subscriptionState.collectAsState()
    val checkoutUrlState by viewModel.checkoutUrlState.collectAsState()
    val portalUrlState by viewModel.portalUrlState.collectAsState()
    val userTier by viewModel.userTier.collectAsState()

    // Handle checkout URL
    LaunchedEffect(checkoutUrlState) {
        if (checkoutUrlState is ResultData.Complete) {
            onOpenUrl((checkoutUrlState as ResultData.Complete<String>).data)
            viewModel.clearCheckoutUrl()
        }
    }

    // Handle portal URL
    LaunchedEffect(portalUrlState) {
        if (portalUrlState is ResultData.Complete) {
            onOpenUrl((portalUrlState as ResultData.Complete<String>).data)
            viewModel.clearPortalUrl()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Current Plan Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = WriteopiaTheme.colorScheme.optionsSelector
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (userTier == Tier.PREMIUM) WrIcons.zap else WrIcons.person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (userTier == Tier.PREMIUM)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Current Plan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Text(
                    text = if (userTier == Tier.PREMIUM) "Premium" else "Free",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (subscriptionState is ResultData.Complete) {
                    val subscription = (subscriptionState as ResultData.Complete<SubscriptionResponse>).data
                    if (subscription.cancelAtPeriodEnd) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Subscription will end at period end",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Plan Features
        if (userTier == Tier.FREE) {
            // Free tier - show upgrade option
            PremiumFeaturesCard()

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.createCheckoutSession() },
                modifier = Modifier.fillMaxWidth(),
                enabled = checkoutUrlState !is ResultData.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (checkoutUrlState is ResultData.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Upgrade to Premium")
                }
            }

            if (checkoutUrlState is ResultData.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Failed to start checkout. Please try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            // Premium tier - show manage option
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Premium Benefits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FeatureItem(text = "Cloud sync across all devices", enabled = true)
                    FeatureItem(text = "Publish notes online", enabled = true)
                    FeatureItem(text = "Priority support", enabled = true)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { viewModel.createPortalSession() },
                modifier = Modifier.fillMaxWidth(),
                enabled = portalUrlState !is ResultData.Loading
            ) {
                if (portalUrlState is ResultData.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Manage Subscription")
                }
            }

            if (portalUrlState is ResultData.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Failed to open portal. Please try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PremiumFeaturesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Upgrade to Premium",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Unlock all features and sync your notes across devices",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            FeatureItem(text = "Cloud sync across all devices", enabled = true)
            FeatureItem(text = "Publish notes online", enabled = true)
            FeatureItem(text = "Priority support", enabled = true)
        }
    }
}

@Composable
private fun FeatureItem(text: String, enabled: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = if (enabled) "✓ $text" else "✗ $text",
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
