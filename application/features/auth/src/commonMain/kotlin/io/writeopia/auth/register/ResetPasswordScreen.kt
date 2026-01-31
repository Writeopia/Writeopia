package io.writeopia.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.max
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RegisterPasswordScreen(
    modifier: Modifier = Modifier,
    passwordState: StateFlow<String>,
    repeatPasswordState: StateFlow<String>,
    resetPasswordState: StateFlow<ResultData<Boolean>>,
    passwordChanged: (String) -> Unit,
    repeatPasswordChanged: (String) -> Unit,
    onPasswordResetRequest: () -> Unit,
    onPasswordResetSuccess: () -> Unit,
    navigateBack: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().widthIn(430.dp)) {
        Icon(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp)
                .clip(CircleShape)
                .clickable(onClick = navigateBack)
                .padding(6.dp),
            imageVector = WrIcons.backArrowDesktop,
            contentDescription = "Arrow back",
            tint = MaterialTheme.colorScheme.onBackground
        )

        val registerScreen = @Composable { modifier: Modifier ->
            ResetPasswordContent(
                passwordState,
                repeatPasswordState,
                passwordChanged,
                repeatPasswordChanged,
                onPasswordResetRequest,
                modifier
            )
        }

        when (val register = resetPasswordState.collectAsState().value) {
            is ResultData.Complete -> {
                if (register.data) {
                    LaunchedEffect(key1 = "navigation") {
                        onPasswordResetSuccess()
                    }
                }

                registerScreen(Modifier)
            }

            is ResultData.Idle, is ResultData.Error -> {
                registerScreen(Modifier)
            }

            is ResultData.Loading, is ResultData.InProgress -> {
                registerScreen(Modifier.blur(6.dp))

                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun BoxScope.ResetPasswordContent(
    passwordState: StateFlow<String>,
    repeatPasswordState: StateFlow<String>,
    passwordChanged: (String) -> Unit,
    repeatPasswordChanged: (String) -> Unit,
    onPasswordResetRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val password by passwordState.collectAsState()
    val repeatPassword by repeatPasswordState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
    var showRepeatPassword by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.large

    Column(
        modifier = modifier
            .padding(horizontal = 50.dp)
            .widthIn(max = 430.dp)
            .align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            WrStrings.resetPassword(),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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

        TextButton(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .background(MaterialTheme.colorScheme.primary, shape = shape)
                .fillMaxWidth(),
            onClick = onPasswordResetRequest
        ) {
            Text(
                text = WrStrings.createAccount(),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
