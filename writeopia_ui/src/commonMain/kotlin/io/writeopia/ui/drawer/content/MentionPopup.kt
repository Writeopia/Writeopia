package io.writeopia.ui.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.writeopia.sdk.models.user.MentionUser

private val MIN_MENTION_DROPDOWN_WIDTH = 112.dp

fun filterMentionUsers(
    users: List<MentionUser>,
    filter: String,
): List<MentionUser> {
    if (filter.isEmpty()) return users
    return users.filter { user ->
        user.username.contains(filter, ignoreCase = true) ||
            user.name.contains(filter, ignoreCase = true)
    }
}

@Composable
fun MentionPopup(
    expanded: Boolean,
    users: List<MentionUser>,
    selectedIndex: Int = 0,
    onUserSelected: (MentionUser) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded && users.isNotEmpty(),
        onDismissRequest = onDismiss,
        modifier = modifier.requiredWidthIn(
            min = MIN_MENTION_DROPDOWN_WIDTH,
            max = Dp.Unspecified,
        ),
        properties = PopupProperties(focusable = false),
    ) {
        users.forEachIndexed { index, user ->
            DropdownMenuItem(
                modifier = Modifier
                    .requiredWidthIn(min = MIN_MENTION_DROPDOWN_WIDTH, max = Dp.Unspecified)
                    .background(
                        if (index == selectedIndex) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        },
                    ),
                text = { MentionUserItem(user) },
                onClick = { onUserSelected(user) },
            )
        }
    }
}

@Composable
private fun MentionUserItem(user: MentionUser) {
    Column {
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}
