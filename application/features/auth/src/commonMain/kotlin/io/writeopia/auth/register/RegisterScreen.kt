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
import io.writeopia.auth.utils.loginScreen
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun RegisterScreen(
    nameState: StateFlow<String>,
    emailState: StateFlow<String>,
    passwordState: StateFlow<String>,
    registerState: StateFlow<ResultData<Boolean>>,
    nameChanged: (String) -> Unit,
    emailChanged: (String) -> Unit,
    passwordChanged: (String) -> Unit,
    onRegisterRequest: () -> Unit,
    onRegisterSuccess: () -> Unit,
    navigateBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
            RegisterContent(
                nameState,
                emailState,
                passwordState,
                nameChanged,
                emailChanged,
                passwordChanged,
                onRegisterRequest,
                modifier
            )
        }

        when (val register = registerState.collectAsState().value) {
            is ResultData.Complete -> {
                if (register.data) {
                    LaunchedEffect(key1 = "navigation") {
                        onRegisterSuccess()
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
private fun BoxScope.RegisterContent(
    nameState: StateFlow<String>,
    emailState: StateFlow<String>,
    passwordState: StateFlow<String>,
    nameChanged: (String) -> Unit,
    emailChanged: (String) -> Unit,
    passwordChanged: (String) -> Unit,
    onRegisterRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name by nameState.collectAsState()
    val email by emailState.collectAsState()
    val password by passwordState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.large

    Column(
        modifier = modifier
            .padding(horizontal = 50.dp)
            .align(Alignment.Center)
            .loginScreen(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Create your account",
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Your journey begins here.",
            color = WriteopiaTheme.colorScheme.textLight,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            name,
            onValueChange = nameChanged,
            shape = shape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            singleLine = true,
            placeholder = {
                Text("Name")
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .background(MaterialTheme.colorScheme.primary, shape = shape)
                .fillMaxWidth(),
            onClick = onRegisterRequest
        ) {
            Text(
                text = "Register",
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Preview
@Composable
fun AuthScreenPreview() {
    RegisterScreen(
        nameState = MutableStateFlow(""),
        emailState = MutableStateFlow(""),
        passwordState = MutableStateFlow(""),
        registerState = MutableStateFlow(ResultData.Idle()),
        nameChanged = {},
        emailChanged = {},
        passwordChanged = {},
        onRegisterRequest = {},
        onRegisterSuccess = {},
        navigateBack = {}
    )
}
