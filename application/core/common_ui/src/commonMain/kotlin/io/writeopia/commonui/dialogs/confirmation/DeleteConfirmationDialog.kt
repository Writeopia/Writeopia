package io.writeopia.commonui.dialogs.confirmation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
        }
    }
}
