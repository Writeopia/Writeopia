package io.writeopia.commonui.dialogs.confirmation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.resources.WrStrings

@Composable
fun DeleteConfirmationDialog(
    onConfirmation: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onCancel) {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(start = 30.dp, end = 30.dp, bottom = 12.dp)
                    .width(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    WrStrings.confirmDeleteMultipleItems(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (LocalPlatform.current.isMobile()) {
                    MobileActions(onConfirmation = onConfirmation, onCancel = onCancel)
                } else {
                    DesktopActions(onConfirmation = onConfirmation, onCancel = onCancel)
                }
            }
        }
    }
}

@Composable
private fun DesktopActions(onConfirmation: () -> Unit, onCancel: () -> Unit) {
    Row {
        Text(
            WrStrings.cancel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onCancel)
                .padding(6.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            WrStrings.delete(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onConfirmation)
                .padding(6.dp)
        )
    }
}

/**
 * On a phone the two actions fill the width of the dialog as tall buttons, so each is an easy
 * target for a finger. The destructive one is the only one in red.
 */
@Composable
private fun MobileActions(onConfirmation: () -> Unit, onCancel: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        MobileAction(
            text = WrStrings.cancel(),
            color = MaterialTheme.colorScheme.onBackground,
            onClick = onCancel
        )

        Spacer(modifier = Modifier.width(12.dp))

        MobileAction(
            text = WrStrings.delete(),
            color = Color.Red,
            onClick = onConfirmation
        )
    }
}

@Composable
private fun RowScope.MobileAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1F)
            .heightIn(min = MOBILE_ACTION_MIN_HEIGHT.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    )
}

private const val MOBILE_ACTION_MIN_HEIGHT = 48
