package io.writeopia.auth.email

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.auth.utils.arrowPadding
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.common.utils.icons.PlatformIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.drawer.factory.isEnterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * First step of sign-up: the email to verify, before any account details are asked for.
 */
@Composable
fun RegisterEmailScreen(
    modifier: Modifier = Modifier,
    emailState: StateFlow<String>,
    sendCodeState: StateFlow<ResultData<Boolean>>,
    canSendCodeState: StateFlow<Boolean>,
    emailChanged: (String) -> Unit,
    onSendCode: () -> Unit,
    navigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val sendCodeErrorMessage = WrStrings.verificationCodeFailed()
    val email by emailState.collectAsState()
    val sendCodeStateValue by sendCodeState.collectAsState()
    val canSendCode by canSendCodeState.collectAsState()
    val isLoading = sendCodeStateValue is ResultData.Loading
    val shape = MaterialTheme.shapes.large

    LaunchedEffect(sendCodeStateValue) {
        if (sendCodeStateValue is ResultData.Error) {
            snackbarHostState.showSnackbar(sendCodeErrorMessage)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .then(if (isLoading) Modifier.blur(6.dp) else Modifier)
                .widthIn(max = 430.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 100.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                WrStrings.createYourAccount(),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                WrStrings.registerEnterEmail(),
                color = WriteopiaTheme.colorScheme.textLighter,
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                email,
                onValueChange = emailChanged,
                shape = shape,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key.isEnterKey() && keyEvent.type == KeyEventType.KeyUp && canSendCode) {
                            onSendCode()
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                label = {
                    Text(WrStrings.email())
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (canSendCode) onSendCode() }
                ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(
                        if (canSendCode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        },
                        shape = shape
                    )
                    .fillMaxWidth(),
                onClick = onSendCode,
                enabled = canSendCode
            ) {
                Text(
                    text = WrStrings.sendVerificationCode(),
                    color = if (canSendCode) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                    }
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (LocalPlatform.current != PlatformType.WEB) {
            Icon(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .arrowPadding()
                    .clip(CircleShape)
                    .clickable(onClick = navigateBack)
                    .padding(6.dp),
                imageVector = PlatformIcons.backArrowMobile,
                contentDescription = "Arrow back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Preview
@Composable
fun RegisterEmailScreenPreview() {
    RegisterEmailScreen(
        emailState = MutableStateFlow(""),
        sendCodeState = MutableStateFlow(ResultData.Idle()),
        canSendCodeState = MutableStateFlow(false),
        emailChanged = {},
        onSendCode = {},
        navigateBack = {}
    )
}
