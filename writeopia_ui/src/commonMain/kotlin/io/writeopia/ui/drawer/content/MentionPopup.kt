package io.writeopia.ui.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.writeopia.sdk.models.user.MentionUser

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
    users: List<MentionUser>,
    selectedIndex: Int = 0,
    onUserSelected: (MentionUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (users.isEmpty()) return

    Card(
        modifier = modifier
            .widthIn(min = 240.dp)
            .width(IntrinsicSize.Max),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = "Users",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            users.forEachIndexed { index, user ->
                MentionUserItem(
                    user = user,
                    isSelected = index == selectedIndex,
                    onClick = { onUserSelected(user) }
                )
            }
        }
    }
}

@Composable
private fun MentionUserItem(
    user: MentionUser,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                }
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = "@${user.username}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
