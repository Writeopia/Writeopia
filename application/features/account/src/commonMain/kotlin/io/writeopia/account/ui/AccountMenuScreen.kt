package io.writeopia.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme

@Composable
fun AccountMenuScreen(
    navigateToAppearance: () -> Unit,
    navigateToTeams: () -> Unit,
    navigateToAccount: () -> Unit,
    navigateToSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Menu sections
        SettingsMenuItem(
            title = WrStrings.appearance(),
            icon = WrIcons.colorModeLight,
            onClick = navigateToAppearance
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsMenuItem(
            title = WrStrings.teams(),
            icon = WrIcons.group,
            onClick = navigateToTeams
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsMenuItem(
            title = WrStrings.account(),
            icon = WrIcons.person,
            onClick = navigateToAccount
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsMenuItem(
            title = WrStrings.subscription(),
            icon = WrIcons.zap,
            onClick = navigateToSubscription
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = WrStrings.version(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SettingsMenuItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(WriteopiaTheme.colorScheme.optionsSelector)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = WrIcons.arrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
