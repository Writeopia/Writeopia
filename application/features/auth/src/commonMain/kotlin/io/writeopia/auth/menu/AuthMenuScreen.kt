package io.writeopia.auth.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.writeopia.auth.utils.loginScreen
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.icons.WrIcons
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun AuthMenuScreen(
    modifier: Modifier = Modifier,
    isConnectedState: StateFlow<ResultData<Boolean>>,
    emailState: StateFlow<String>,
    passwordState: StateFlow<String>,
    loginState: StateFlow<ResultData<Boolean>>,
    emailChanged: (String) -> Unit,
    passwordChanged: (String) -> Unit,
    onLoginRequest: () -> Unit,
    onLoginSuccess: () -> Unit,
    saveUserChoiceOffline: () -> Unit,
    navigateToLogin: () -> Unit,
    navigateToRegister: () -> Unit,
    navigateToApp: () -> Unit,
) {

    Box(modifier = modifier.fillMaxSize()) {
        when (val isConnected = isConnectedState.collectAsState().value) {
            is ResultData.Complete -> {
                if (isConnected.data) {
                    LaunchedEffect("navigateUp") {
                        navigateToApp()
                    }
                } else {
                    AuthMenuContentScreen(
                        emailState,
                        passwordState,
                        loginState,
                        emailChanged,
                        passwordChanged,
                        onLoginRequest,
                        onLoginSuccess,
                        navigateToRegister,
                        navigateToApp
                    )
                }
            }

            is ResultData.Error -> {
                AuthMenuContentScreen(
                    emailState,
                    passwordState,
                    loginState,
                    emailChanged,
                    passwordChanged,
                    onLoginRequest,
                    onLoginSuccess,
                    navigateToRegister,
                    navigateToApp
                )
            }

            is ResultData.Idle, is ResultData.Loading, is ResultData.InProgress -> {
                LoadingScreen()
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun AuthMenuContentScreen(
    emailState: StateFlow<String>,
    passwordState: StateFlow<String>,
    loginState: StateFlow<ResultData<Boolean>>,
    emailChanged: (String) -> Unit,
    passwordChanged: (String) -> Unit,
    onLoginRequest: () -> Unit,
    onLoginSuccess: () -> Unit,
    navigateToRegister: () -> Unit,
    navigateToApp: () -> Unit
) {
    val email by emailState.collectAsState()
    val password by passwordState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).loginScreen(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            val shape = MaterialTheme.shapes.large

            Text(
                "Let's start now!",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Sign in to your account at Writeopia.",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                email,
                onValueChange = emailChanged,
                shape = shape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                singleLine = true,
                placeholder = {
                    Text("Email")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Spacer(modifier = Modifier.height(12.dp))

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
                    Text("Password")
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

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1F))

                Text(
                    "OR",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(modifier = Modifier.weight(1F))
            }

            TextButton(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = shape)
                    .fillMaxWidth(),
                onClick = navigateToRegister
            ) {
                Text(
                    text = "Create an account",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
