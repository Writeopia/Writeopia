package io.writeopia.auth.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.writeopia.auth.google.GoogleCredential
import io.writeopia.auth.google.GoogleSignInResult
import io.writeopia.auth.google.rememberGoogleSignInLauncher
import io.writeopia.auth.utils.arrowPadding
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.common.utils.icons.PlatformIcons
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.drawer.factory.isEnterKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun AuthMenuScreen(
    modifier: Modifier = Modifier,
    emailState: StateFlow<String>,
    passwordState: StateFlow<String>,
    loginState: StateFlow<ResultData<Boolean>>,
    accountDeletionPending: StateFlow<Boolean>,
    emailChanged: (String) -> Unit,
    passwordChanged: (String) -> Unit,
    onLoginRequest: () -> Unit,
    onGoogleLoginRequest: (GoogleCredential) -> Unit = {},
    onGoogleSignInFailed: (Throwable) -> Unit = {},
    navigateToRegister: () -> Unit,
    navigateToForgotPassword: () -> Unit,
    navigateUp: () -> Unit,
    navigateNext: () -> Unit,
    navigateToAccountDeletionPending: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val loginErrorMessage = WrStrings.loginFailed()
    val loginStateValue by loginState.collectAsState()
    val accountDeletionPendingValue by accountDeletionPending.collectAsState()

    LaunchedEffect(accountDeletionPendingValue) {
        if (accountDeletionPendingValue) {
            navigateToAccountDeletionPending()
        }
    }

    LaunchedEffect(loginStateValue) {
        if (loginStateValue is ResultData.Error && !accountDeletionPendingValue) {
            snackbarHostState.showSnackbar(loginErrorMessage)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val authScreen = @Composable { modifier: Modifier ->
            AuthMenuContentScreen(
                emailState,
                passwordState,
                emailChanged,
                passwordChanged,
                onLoginRequest,
                onGoogleLoginRequest,
                onGoogleSignInFailed,
                navigateToRegister,
                navigateToForgotPassword,
                modifier,
            )
        }

        when (val isConnected = loginStateValue) {
            is ResultData.Complete -> {
                if (isConnected.data) {
                    LaunchedEffect("navigateUp") {
                        navigateNext()
                    }
                }
                authScreen(Modifier)
            }

            is ResultData.Error -> {
                println("auth error")
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

        if (LocalPlatform.current != PlatformType.WEB) {
            Icon(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .arrowPadding()
                    .clip(CircleShape)
                    .clickable {
                        navigateUp()
                    }
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
    onGoogleLoginRequest: (GoogleCredential) -> Unit,
    onGoogleSignInFailed: (Throwable) -> Unit,
    navigateToRegister: () -> Unit,
    navigateToForgotPassword: () -> Unit,
    modifier: Modifier = Modifier
) {
    val email by emailState.collectAsState()
    val password by passwordState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 430.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 100.dp, bottom = 40.dp),
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
                label = {
                    Text(WrStrings.emailOrUsername())
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                password,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key.isEnterKey() &&
                            keyEvent.type == KeyEventType.KeyUp &&
                            email.isNotBlank() &&
                            password.isNotBlank()
                        ) {
                            onLoginRequest()
                            true
                        } else {
                            false
                        }
                    },
                onValueChange = passwordChanged,
                shape = shape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            onLoginRequest()
                        }
                    }
                ),
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                label = {
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = WrStrings.forgotPassword(),
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = navigateToForgotPassword)
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val googleSignInLauncher = rememberGoogleSignInLauncher()
            if (googleSignInLauncher != null) {
                // Only between the two ways to sign in, like in the native apps.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1F))

                    Text(
                        WrStrings.or(),
                        color = WriteopiaTheme.colorScheme.textLighter,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    HorizontalDivider(modifier = Modifier.weight(1F))
                }

                val scope = rememberCoroutineScope()
                var googleInProgress by remember { mutableStateOf(false) }

                GoogleSignInButton(
                    inProgress = googleInProgress,
                    shape = shape,
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                    onClick = {
                        // UNDISPATCHED keeps the platform call inside the click event, which the
                        // web popup needs to not be blocked by the browser.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            googleInProgress = true
                            try {
                                when (val result = googleSignInLauncher.signIn()) {
                                    is GoogleSignInResult.Success -> onGoogleLoginRequest(result.credential)
                                    is GoogleSignInResult.Failure -> onGoogleSignInFailed(result.error)
                                    GoogleSignInResult.Cancelled -> Unit
                                }
                            } finally {
                                googleInProgress = false
                            }
                        }
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // "New to Writeopia? Create an account", a link like in the native apps.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = WrStrings.newToWriteopia(),
                    color = WriteopiaTheme.colorScheme.textLighter,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = WrStrings.createAnAccount(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = navigateToRegister)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .testTag("AuthMenu.register"),
                )
            }
        }
    }
}

/**
 * "Continue with Google", outlined so it stays second to the email sign-in, with Google's own
 * four-color "G" ([GoogleLogo]). Shows a spinner while the Google page is open (the desktop browser, the
 * Android account picker, the web popup).
 */
@Composable
private fun GoogleSignInButton(
    inProgress: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !inProgress,
        shape = shape,
        border = BorderStroke(1.dp, DividerDefaults.color),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onBackground,
            disabledContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = modifier.heightIn(min = 48.dp).testTag("AuthMenu.google"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (inProgress) 0F else 1F),
            ) {
                Image(
                    imageVector = GoogleLogo,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = WrStrings.continueWithGoogle(),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (inProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
