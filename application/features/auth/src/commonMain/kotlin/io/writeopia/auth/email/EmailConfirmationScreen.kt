package io.writeopia.auth.email

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.auth.utils.arrowPadding
import io.writeopia.common.utils.icons.PlatformIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.drawer.factory.isEnterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun EmailConfirmationScreen(
    modifier: Modifier = Modifier,
    emailState: StateFlow<String>,
    codeState: StateFlow<String>,
    confirmState: StateFlow<ResultData<Boolean>>,
    resendState: StateFlow<ResultData<Boolean>>,
    resendCooldownSeconds: StateFlow<Int>,
    codeChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    navigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val invalidCodeMessage = WrStrings.invalidCode()
    val emailConfirmedMessage = WrStrings.emailConfirmed()
    val codeSentMessage = WrStrings.codeSent()
    val confirmStateValue by confirmState.collectAsState()
    val resendStateValue by resendState.collectAsState()

    LaunchedEffect(confirmStateValue) {
        if (confirmStateValue is ResultData.Error) {
            snackbarHostState.showSnackbar(invalidCodeMessage)
        }
    }

    LaunchedEffect(resendStateValue) {
        when (resendStateValue) {
            is ResultData.Complete -> {
                snackbarHostState.showSnackbar(codeSentMessage)
            }
            is ResultData.Error -> {
                snackbarHostState.showSnackbar("Failed to resend code")
            }
            else -> {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val content = @Composable { contentModifier: Modifier ->
            EmailConfirmationContent(
                emailState = emailState,
                codeState = codeState,
                codeChanged = codeChanged,
                onConfirm = onConfirm,
                onResend = onResend,
                resendState = resendState,
                resendCooldownSeconds = resendCooldownSeconds,
                modifier = contentModifier
            )
        }

        when (confirmStateValue) {
            is ResultData.Loading, is ResultData.InProgress -> {
                content(Modifier.blur(6.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                content(Modifier)
            }
        }

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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = if (confirmStateValue is ResultData.Error || resendStateValue is ResultData.Error) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (confirmStateValue is ResultData.Error || resendStateValue is ResultData.Error) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
    }
}

@Composable
private fun BoxScope.EmailConfirmationContent(
    emailState: StateFlow<String>,
    codeState: StateFlow<String>,
    codeChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    resendState: StateFlow<ResultData<Boolean>>,
    resendCooldownSeconds: StateFlow<Int>,
    modifier: Modifier = Modifier,
) {
    val email by emailState.collectAsState()
    val code by codeState.collectAsState()
    val resendStateValue by resendState.collectAsState()
    val cooldownSeconds by resendCooldownSeconds.collectAsState()
    val shape = MaterialTheme.shapes.large

    Column(
        modifier = modifier
            .widthIn(max = 430.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 100.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            WrStrings.confirmYourEmail(),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            WrStrings.weSentCodeTo(email),
            color = WriteopiaTheme.colorScheme.textLighter,
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = code,
            onValueChange = codeChanged,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.key.isEnterKey() && keyEvent.type == KeyEventType.KeyUp) {
                        onConfirm()
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
            placeholder = {
                Text(WrStrings.enterCode())
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center,
                letterSpacing = MaterialTheme.typography.headlineSmall.fontSize * 0.3
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .background(MaterialTheme.colorScheme.primary, shape = shape)
                .fillMaxWidth(),
            onClick = onConfirm,
            enabled = code.length == 6
        ) {
            Text(
                text = WrStrings.confirm(),
                color = if (code.length == 6) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val isResendEnabled = resendStateValue !is ResultData.Loading && cooldownSeconds == 0

        TextButton(
            onClick = onResend,
            enabled = isResendEnabled
        ) {
            if (resendStateValue is ResultData.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
            }
            Text(
                text = if (cooldownSeconds > 0) {
                    "${WrStrings.resendCode()} (${cooldownSeconds}s)"
                } else {
                    WrStrings.resendCode()
                },
                color = if (isResendEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                }
            )
        }
    }
}

@Preview
@Composable
fun EmailConfirmationScreenPreview() {
    EmailConfirmationScreen(
        emailState = MutableStateFlow("test@example.com"),
        codeState = MutableStateFlow(""),
        confirmState = MutableStateFlow(ResultData.Idle()),
        resendState = MutableStateFlow(ResultData.Idle()),
        resendCooldownSeconds = MutableStateFlow(0),
        codeChanged = {},
        onConfirm = {},
        onResend = {},
        navigateBack = {}
    )
}
