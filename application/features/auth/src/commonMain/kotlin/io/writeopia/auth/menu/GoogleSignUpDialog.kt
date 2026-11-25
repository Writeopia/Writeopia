package io.writeopia.auth.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.writeopia.auth.register.TermsAgreement
import io.writeopia.resources.WrStrings

/**
 * The first sign-in of a Google account: the username of the new account (Google's suggestion
 * to start with) and the agreement to the Terms and Privacy Policy.
 */
@Composable
internal fun GoogleSignUpDialog(
    state: GoogleSignUpState,
    onUsernameChanged: (String) -> Unit,
    onTermsAcceptedChanged: (Boolean) -> Unit,
    onOpenLegalDocument: (type: String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.inProgress) onDismiss() },
        title = { Text(WrStrings.googleSignUpTitle()) },
        text = {
            Column {
                Text(
                    WrStrings.googleSignUpMessage(state.email),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChanged,
                    label = { Text(WrStrings.username()) },
                    singleLine = true,
                    isError = state.error == GoogleSignUpError.USERNAME_TAKEN,
                    supportingText = if (state.error == GoogleSignUpError.USERNAME_TAKEN) {
                        { Text(WrStrings.usernameTaken()) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("GoogleSignUp.username"),
                )

                Spacer(modifier = Modifier.height(8.dp))

                TermsAgreement(
                    accepted = state.termsAccepted,
                    onAcceptedChanged = onTermsAcceptedChanged,
                    onOpenLegalDocument = onOpenLegalDocument,
                )

                if (state.error == GoogleSignUpError.FAILED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        WrStrings.registrationFailed(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.canCreate,
                modifier = Modifier.testTag("GoogleSignUp.create"),
            ) {
                Text(WrStrings.createAccount())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.inProgress) {
                Text(WrStrings.cancel())
            }
        },
    )
}
