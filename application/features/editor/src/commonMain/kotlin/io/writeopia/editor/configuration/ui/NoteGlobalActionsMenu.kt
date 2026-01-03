package io.writeopia.editor.configuration.ui

// import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.FontOptions
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.LockButton
import io.writeopia.model.Font
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

// @Preview
@Composable
internal fun NoteGlobalActionsMenu(
    isEditableState: StateFlow<Boolean>,
    setEditable: () -> Unit,
    onShareJson: () -> Unit = {},
    onShareMd: () -> Unit = {},
    changeFontFamily: (Font) -> Unit,
    selectedState: StateFlow<Font>,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(modifier = Modifier.widthIn(max = 500.dp)) {
            Spacer(modifier = Modifier.height(16.dp))

            Title(WrStrings.actions())

            Spacer(modifier = Modifier.height(8.dp))

            LockButton(
                isEditableState,
                setEditable,
//            selectedColor = WriteopiaTheme.colorScheme.highlight,
//            defaultColor = MaterialTheme.colorScheme.background
            )

            Spacer(modifier = Modifier.height(16.dp))

            Title(WrStrings.font())

            Spacer(modifier = Modifier.height(8.dp))

            FontOptions(
                modifier = Modifier.widthIn(max = 300.dp),
                changeFontFamily = changeFontFamily,
                selectedState = selectedState,
                selectedColor = WriteopiaTheme.colorScheme.highlight,
                defaultColor = MaterialTheme.colorScheme.background
            )

            Spacer(modifier = Modifier.height(16.dp))

            Title(WrStrings.export())

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                ShareButton(
                    text = WrStrings.exportJson(),
                    onClick = onShareJson
                )

                Spacer(modifier = Modifier.width(8.dp))

                ShareButton(
                    text = WrStrings.exportMarkdown(),
                    onClick = onShareMd
                )
            }
        }
    }
}

@Composable
private fun Title(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun ShareButton(text: String, onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondary)
            .padding(8.dp),
        text = text,
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold
        )
    )
}
