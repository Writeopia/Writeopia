package io.writeopia.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.ui.icons.WrSdkIcons

enum class SpreadsheetActionType {
    COLUMN,
    ROW
}

sealed class SpreadsheetAction {
    data object AddBefore : SpreadsheetAction()

    data object AddAfter : SpreadsheetAction()

    data object MoveBefore : SpreadsheetAction()

    data object MoveAfter : SpreadsheetAction()

    data object Delete : SpreadsheetAction()
}

@Composable
fun EditSpreadsheetDialog(
    actionType: SpreadsheetActionType,
    onDismissRequest: () -> Unit,
    onAction: (SpreadsheetAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (actionType) {
        SpreadsheetActionType.COLUMN -> "Edit Column"
        SpreadsheetActionType.ROW -> "Edit Row"
    }

    val (beforeLabel, afterLabel) = when (actionType) {
        SpreadsheetActionType.COLUMN -> "left" to "right"
        SpreadsheetActionType.ROW -> "above" to "below"
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                DialogOption(
                    icon = WrSdkIcons.plus,
                    text = "Add $beforeLabel",
                    onClick = {
                        onAction(SpreadsheetAction.AddBefore)
                        onDismissRequest()
                    }
                )

                DialogOption(
                    icon = WrSdkIcons.plus,
                    text = "Add $afterLabel",
                    onClick = {
                        onAction(SpreadsheetAction.AddAfter)
                        onDismissRequest()
                    }
                )

                DialogOption(
                    icon = WrSdkIcons.smallArrowUp,
                    text = "Move $beforeLabel",
                    onClick = {
                        onAction(SpreadsheetAction.MoveBefore)
                        onDismissRequest()
                    }
                )

                DialogOption(
                    icon = WrSdkIcons.smallArrowDown,
                    text = "Move $afterLabel",
                    onClick = {
                        onAction(SpreadsheetAction.MoveAfter)
                        onDismissRequest()
                    }
                )

                DialogOption(
                    icon = WrSdkIcons.delete,
                    text = "Delete",
                    onClick = {
                        onAction(SpreadsheetAction.Delete)
                        onDismissRequest()
                    },
                    isDestructive = true
                )
            }
        }
    }
}

@Composable
private fun DialogOption(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val color = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(20.dp),
            tint = color.copy(alpha = 0.7f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
