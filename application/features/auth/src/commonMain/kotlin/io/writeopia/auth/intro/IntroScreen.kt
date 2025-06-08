package io.writeopia.auth.intro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme

@Composable
fun IntroScreen(
    signInClick: () -> Unit,
    offlineUsageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = MaterialTheme.shapes.large

        Text(
            "Welcome to Writeopia",
            style = MaterialTheme.typography.titleLarge,
            color = WriteopiaTheme.colorScheme.textLight
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "A text editor for the free",
            style = MaterialTheme.typography.bodyMedium,
            color = WriteopiaTheme.colorScheme.textLighter
        )

        Spacer(modifier = Modifier.height(40.dp))

        TextButton(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .background(WriteopiaTheme.colorScheme.defaultButton, shape = shape)
                .fillMaxWidth(),
            onClick = signInClick,
            contentPadding = PaddingValues(0.dp),
            shape = shape
        ) {
            Text(
                text = "Sign in",
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1F))

            Text(
                WrStrings.or(),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(modifier = Modifier.weight(1F))
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .background(WriteopiaTheme.colorScheme.defaultButton, shape = shape)
                .fillMaxWidth(),
            onClick = offlineUsageClick,
            contentPadding = PaddingValues(0.dp),
            shape = shape
        ) {
            Text(
                text = "Offline use",
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
