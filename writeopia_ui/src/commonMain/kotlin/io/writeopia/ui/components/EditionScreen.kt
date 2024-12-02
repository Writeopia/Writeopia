package io.writeopia.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

// Here
// This screen could live in a module for extra Composables. In the future there will be more
// buttons here
@Composable
fun EditionScreen(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    Box(modifier = modifier) {
        Icon(
            modifier = Modifier
                .align(Alignment.Center)
                .size(50.dp)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete),
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "Delete",
//            contentDescription = stringResource(R.string.delete),
            tint = MaterialTheme.colorScheme.onPrimary
        )

        Icon(
            modifier = Modifier
                .size(50.dp)
                .align(Alignment.CenterEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClose),
            imageVector = Icons.Outlined.Close,
            contentDescription = "Delete",
//            contentDescription = stringResource(R.string.delete),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Preview
@Composable
fun EditionScreenPreview() {
    EditionScreen()
}
