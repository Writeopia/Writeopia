package io.writeopia.auth.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.writeopia.auth.utils.loginScreen
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AuthMenuScreen(
    modifier: Modifier = Modifier,
    emailState: StateFlow<String>,
    passwordState: StateFlow<String>,
    loginState: StateFlow<ResultData<Boolean>>,
    emailChanged: (String) -> Unit,
    passwordChanged: (String) -> Unit,
    onLoginRequest: () -> Unit,
    navigateToRegister: () -> Unit,
    offlineUsage: () -> Unit,
    navigateUp: () -> Unit,
    navigateToApp: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Icon(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp)
                .clip(CircleShape)
                .clickable {
                    navigateUp()
                }
                .padding(6.dp),
            imageVector = WrIcons.backArrowDesktop,
            contentDescription = "Arrow back",
            tint = MaterialTheme.colorScheme.onBackground
        )

        val authScreen = @Composable { modifier: Modifier ->
            AuthMenuContentScreen(
                emailState,
                passwordState,
                emailChanged,
                passwordChanged,
                onLoginRequest,
                navigateToRegister,
                offlineUsage,
                modifier,
            )
        }

        when (val isConnected = loginState.collectAsState().value) {
            is ResultData.Complete -> {
                if (isConnected.data) {
                    LaunchedEffect("navigateUp") {
                        navigateToApp()
                    }
                }
                authScreen(Modifier)
            }

            is ResultData.Error -> {
                authScreen(Modifier)
            }

            is ResultData.Loading, is ResultData.InProgress -> {
                authScreen(Modifier.blur(8.dp))

                LoadingScreen()
            }

            is ResultData.Idle -> {
                authScreen(Modifier)
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
    emailChanged: (String) -> Unit,
    passwordChanged: (String) -> Unit,
    onLoginRequest: () -> Unit,
    navigateToRegister: () -> Unit,
    offlineUsage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val email by emailState.collectAsState()
    val password by passwordState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).loginScreen(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val shape = MaterialTheme.shapes.large

            Text(
                WrStrings.startNow(),
                color = WriteopiaTheme.colorScheme.textLight,
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                WrStrings.signInToAccount(),
                color = WriteopiaTheme.colorScheme.textLighter,
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
                    Text(WrStrings.email())
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                    Text(WrStrings.password())
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

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = shape)
                    .fillMaxWidth(),
                onClick = onLoginRequest,
                shape = shape
            ) {
                Text(
                    text = WrStrings.singIn(),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1F))

                Text(
                    WrStrings.or(),
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
                    .background(WriteopiaTheme.colorScheme.defaultButton, shape = shape)
                    .fillMaxWidth(),
                onClick = navigateToRegister,
                contentPadding = PaddingValues(0.dp),
                shape = shape
            ) {
                Text(
                    text = WrStrings.createYourAccount(),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = WrStrings.useOffline(),
                color = MaterialTheme.colorScheme.onBackground,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable(onClick = offlineUsage)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}
