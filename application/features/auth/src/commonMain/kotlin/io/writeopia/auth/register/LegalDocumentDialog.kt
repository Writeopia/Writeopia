package io.writeopia.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.serialization.response.LegalDocumentResponse
import io.writeopia.sdk.serialization.response.LegalSectionResponse

/**
 * Shows the Terms of Service or the Privacy Policy fetched from the API.
 */
@Composable
internal fun LegalDocumentDialog(
    state: LegalDocumentState,
    onRetry: (type: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == LegalDocumentState.Hidden) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (state) {
                    is LegalDocumentState.Loaded -> state.document.title
                    is LegalDocumentState.Loading -> state.type.legalTitle()
                    is LegalDocumentState.Failed -> state.type.legalTitle()
                    LegalDocumentState.Hidden -> ""
                }
            )
        },
        text = {
            when (state) {
                is LegalDocumentState.Loaded -> LegalDocumentBody(state.document)

                is LegalDocumentState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }

                is LegalDocumentState.Failed -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(WrStrings.legalLoadError(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { onRetry(state.type) }) { Text(WrStrings.retry()) }
                }

                LegalDocumentState.Hidden -> Unit
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(WrStrings.close()) }
        }
    )
}

@Composable
private fun LegalDocumentBody(document: LegalDocumentResponse) {
    Column(
        modifier = Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            WrStrings.legalEffective(document.effectiveDate),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        document.sections.forEach { section -> LegalSection(section) }
    }
}

@Composable
private fun LegalSection(section: LegalSectionResponse) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            section.heading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // Paragraphs are separated by a blank line; a line starting with "- " is a bullet.
        section.body.split("\n\n").forEach { paragraph ->
            val lines = paragraph.lines()

            lines.forEach { line ->
                if (line.startsWith("- ")) {
                    Text(
                        "•  ${line.removePrefix("- ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// Only until the document arrives, so the dialog does not flash an empty title.
@Composable
private fun String.legalTitle(): String =
    if (this == LEGAL_PRIVACY) WrStrings.privacyPolicy() else WrStrings.termsOfService()

internal const val LEGAL_TERMS = "terms"
internal const val LEGAL_PRIVACY = "privacy"
