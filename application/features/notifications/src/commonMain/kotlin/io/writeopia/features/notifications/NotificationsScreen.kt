package io.writeopia.features.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navigationClick: () -> Unit,
    nestedScrollConnection: NestedScrollConnection? = null,
    isToolbarVisible: Boolean = true,
    bottomBar: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isToolbarVisible,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it }
            ) {
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
            }
        },
        bottomBar = bottomBar
    ) { paddingValues ->
        // Add extra bottom padding when navigation bar is visible
        val contentBottomPadding by animateDpAsState(
            targetValue = if (isToolbarVisible) 96.dp else 0.dp,
            animationSpec = tween(durationMillis = 300),
            label = "contentBottomPadding"
        )
        val adjustedPaddingValues = PaddingValues(
            start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr),
            top = paddingValues.calculateTopPadding(),
            end = paddingValues.calculateRightPadding(LayoutDirection.Ltr),
            bottom = paddingValues.calculateBottomPadding() + contentBottomPadding
        )

        val scrollModifier = if (nestedScrollConnection != null) {
            Modifier.fillMaxSize().padding(adjustedPaddingValues).nestedScroll(nestedScrollConnection)
        } else {
            Modifier.fillMaxSize().padding(adjustedPaddingValues)
        }
        Box(modifier = scrollModifier) {
            Text(
                "No notifications",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
