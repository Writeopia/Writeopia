package io.writeopia.auth.forgotpassword

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.writeopia.auth.utils.arrowPadding
import io.writeopia.common.utils.icons.PlatformIcons
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ForgotPasswordNewPasswordScreen(
    modifier: Modifier = Modifier,
    passwordState: StateFlow<String>,
    repeatPasswordState: StateFlow<String>,
    resetPasswordState: StateFlow<ResultData<Boolean>>,
    passwordChanged: (String) -> Unit,
    repeatPasswordChanged: (String) -> Unit,
    onResetPassword: () -> Unit,
    navigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resetPasswordStateValue by resetPasswordState.collectAsState()

    LaunchedEffect(resetPasswordStateValue) {
        if (resetPasswordStateValue is ResultData.Error) {
            val error = (resetPasswordStateValue as ResultData.Error).exception
            snackbarHostState.showSnackbar(error?.message ?: "Failed to reset password")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val content = @Composable { contentModifier: Modifier ->
            ForgotPasswordNewPasswordContent(
                passwordState = passwordState,
                repeatPasswordState = repeatPasswordState,
                passwordChanged = passwordChanged,
                repeatPasswordChanged = repeatPasswordChanged,
                onResetPassword = onResetPassword,
                modifier = contentModifier
            )
        }

        when (resetPasswordStateValue) {
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
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun BoxScope.ForgotPasswordNewPasswordContent(
    passwordState: StateFlow<String>,
    repeatPasswordState: StateFlow<String>,
    passwordChanged: (String) -> Unit,
    repeatPasswordChanged: (String) -> Unit,
    onResetPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val password by passwordState.collectAsState()
    val repeatPassword by repeatPasswordState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
    var showRepeatPassword by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.large

    Column(
        modifier = modifier
            .widthIn(max = 430.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 100.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            WrStrings.setNewPassword(),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            WrStrings.typeNewPassword(),
            color = WriteopiaTheme.colorScheme.textLight,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            password,
            modifier = Modifier.fillMaxWidth(),
            onValueChange = passwordChanged,
            shape = shape,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            placeholder = {
                Text(WrStrings.newPassword())
            },
            trailingIcon = {
                if (showPassword) {
                    Icon(
                        modifier = Modifier.clip(CircleShape)
                            .clickable { showPassword = !showPassword }
                            .padding(4.dp),
                        imageVector = WrIcons.visibilityOff,
                        contentDescription = "Eye closed"
                    )
                } else {
                    Icon(
                        modifier = Modifier.clip(CircleShape)
                            .clickable { showPassword = !showPassword }
                            .padding(4.dp),
                        imageVector = WrIcons.visibilityOn,
                        contentDescription = "Eye open"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            repeatPassword,
            modifier = Modifier.fillMaxWidth(),
            onValueChange = repeatPasswordChanged,
            shape = shape,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showRepeatPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            placeholder = {
                Text(WrStrings.repeatPassword())
            },
            trailingIcon = {
                if (showRepeatPassword) {
                    Icon(
                        modifier = Modifier.clip(CircleShape)
                            .clickable { showRepeatPassword = !showRepeatPassword }
                            .padding(4.dp),
                        imageVector = WrIcons.visibilityOff,
                        contentDescription = "Eye closed"
                    )
                } else {
                    Icon(
                        modifier = Modifier.clip(CircleShape)
                            .clickable { showRepeatPassword = !showRepeatPassword }
                            .padding(4.dp),
                        imageVector = WrIcons.visibilityOn,
                        contentDescription = "Eye open"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val isEnabled = password.isNotBlank() && password == repeatPassword

        TextButton(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = shape)
                .fillMaxWidth(),
            onClick = onResetPassword,
            enabled = isEnabled
        ) {
            Text(
                text = WrStrings.resetPassword(),
                color = if (isEnabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                }
            )
        }
    }
}
