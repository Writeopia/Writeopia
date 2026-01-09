package io.writeopia.features.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navigationClick: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier,
                            text = "Notifications",
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
                navigationIcon = {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = navigationClick)
                                .padding(10.dp),
                            imageVector = WrIcons.backArrowMobile,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        bottomBar = bottomBar
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                "No notifications",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
