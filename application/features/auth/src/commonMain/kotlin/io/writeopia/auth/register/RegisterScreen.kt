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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.writeopia.auth.utils.PasswordValidationResult
import io.writeopia.auth.utils.PasswordValidator
import io.writeopia.auth.utils.arrowPadding
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.common.utils.icons.PlatformIcons
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.drawer.factory.isEnterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    nameState: StateFlow<String>,
    usernameState: StateFlow<String>,
    emailState: StateFlow<String>,
    companyState: StateFlow<String>,
    passwordState: StateFlow<String>,
    registerState: StateFlow<ResultData<Boolean>>,
    usernameAvailabilityState: StateFlow<UsernameAvailability>,
    passwordValidationState: StateFlow<PasswordValidationResult>,
    canRegisterState: StateFlow<Boolean>,
    termsAcceptedState: StateFlow<Boolean>,
    legalDocumentState: StateFlow<LegalDocumentState>,
    nameChanged: (String) -> Unit,
    usernameChanged: (String) -> Unit,
    companyChanged: (String) -> Unit,
    passwordChanged: (String) -> Unit,
    termsAcceptedChanged: (Boolean) -> Unit,
    onOpenLegalDocument: (type: String) -> Unit,
    onCloseLegalDocument: () -> Unit,
    onRegisterRequest: () -> Unit,
    onRegisterSuccess: () -> Unit,
    navigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val registrationErrorMessage = WrStrings.registrationFailed()
    val registerStateValue by registerState.collectAsState()

    LaunchedEffect(registerStateValue) {
        if (registerStateValue is ResultData.Error) {
            snackbarHostState.showSnackbar(registrationErrorMessage)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val registerScreen = @Composable { modifier: Modifier ->
            RegisterContent(
                nameState,
                usernameState,
                usernameAvailabilityState,
                emailState,
                companyState,
                passwordState,
                passwordValidationState,
                canRegisterState,
                termsAcceptedState,
                nameChanged,
                usernameChanged,
                companyChanged,
                passwordChanged,
                termsAcceptedChanged,
                onOpenLegalDocument,
                onRegisterRequest,
                modifier
            )
        }

        val legalDocument by legalDocumentState.collectAsState()

        LegalDocumentDialog(
            state = legalDocument,
            onRetry = onOpenLegalDocument,
            onDismiss = onCloseLegalDocument
        )

        when (val register = registerStateValue) {
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

@Composable
private fun BoxScope.RegisterContent(
    nameState: StateFlow<String>,
    usernameState: StateFlow<String>,
    usernameAvailabilityState: StateFlow<UsernameAvailability>,
    emailState: StateFlow<String>,
    companyState: StateFlow<String>,
    passwordState: StateFlow<String>,
    passwordValidationState: StateFlow<PasswordValidationResult>,
    canRegisterState: StateFlow<Boolean>,
    termsAcceptedState: StateFlow<Boolean>,
    nameChanged: (String) -> Unit,
    usernameChanged: (String) -> Unit,
    companyChanged: (String) -> Unit,
    passwordChanged: (String) -> Unit,
    termsAcceptedChanged: (Boolean) -> Unit,
    onOpenLegalDocument: (type: String) -> Unit,
    onRegisterRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name by nameState.collectAsState()
    val username by usernameState.collectAsState()
    val usernameAvailability by usernameAvailabilityState.collectAsState()
    val usernameTaken = usernameAvailability == UsernameAvailability.TAKEN
    val company by companyState.collectAsState()
    val email by emailState.collectAsState()
    val password by passwordState.collectAsState()
    val passwordValidation by passwordValidationState.collectAsState()
    val canRegister by canRegisterState.collectAsState()
    val termsAccepted by termsAcceptedState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
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
            WrStrings.createYourAccount(),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            WrStrings.journeyStarts(),
            color = WriteopiaTheme.colorScheme.textLighter,
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
            label = {
                Text(WrStrings.name())
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            username,
            onValueChange = usernameChanged,
            shape = shape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            singleLine = true,
            label = {
                Text(WrStrings.username())
            },
            isError = usernameTaken,
            supportingText = if (usernameTaken) {
                { Text(WrStrings.usernameTaken()) }
            } else {
                null
            },
            trailingIcon = if (usernameAvailability == UsernameAvailability.CHECKING) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            email,
            onValueChange = {},
            shape = shape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            // Verified in the previous step, so it can't change here.
            enabled = false,
            singleLine = true,
            label = {
                Text(WrStrings.email())
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            company,
            onValueChange = companyChanged,
            shape = shape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            singleLine = true,
            label = {
                Text(WrStrings.workspaceName())
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            password,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.key.isEnterKey() && keyEvent.type == KeyEventType.KeyUp && canRegister) {
                        onRegisterRequest()
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
                onDone = { if (canRegister) onRegisterRequest() }
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

        PasswordStrengthIndicator(
            validationResult = passwordValidation,
            password = password,
            modifier = Modifier
        )

        Spacer(modifier = Modifier.height(8.dp))

        TermsAgreement(
            accepted = termsAccepted,
            onAcceptedChanged = termsAcceptedChanged,
            onOpenLegalDocument = onOpenLegalDocument
        )

        Spacer(modifier = Modifier.height(8.dp))

        val buttonColor = if (canRegister) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        }

        val textColor = if (canRegister) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
        }

        TextButton(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .background(buttonColor, shape = shape)
                .fillMaxWidth(),
            onClick = onRegisterRequest,
            enabled = canRegister
        ) {
            Text(
                text = WrStrings.createAccount(),
                color = textColor
            )
        }
    }
}

/**
 * "I agree to the Terms of Service and the Privacy Policy", with both names opening the document.
 */
@Composable
private fun TermsAgreement(
    accepted: Boolean,
    onAcceptedChanged: (Boolean) -> Unit,
    onOpenLegalDocument: (type: String) -> Unit,
) {
    val linkStyle = TextLinkStyles(
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        )
    )
    val prefix = WrStrings.termsAgreePrefix()
    val terms = WrStrings.termsOfService()
    val and = WrStrings.termsAgreeAnd()
    val privacy = WrStrings.privacyPolicy()

    val text = buildAnnotatedString {
        append("$prefix ")
        withLink(LinkAnnotation.Clickable("terms", linkStyle) { onOpenLegalDocument(LEGAL_TERMS) }) {
            append(terms)
        }
        // "l'" in Italian is glued to the word after it.
        append(if (and.endsWith("'")) " $and" else " $and ")
        withLink(LinkAnnotation.Clickable("privacy", linkStyle) { onOpenLegalDocument(LEGAL_PRIVACY) }) {
            append(privacy)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = accepted, onCheckedChange = onAcceptedChanged)

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Preview
@Composable
fun AuthScreenPreview() {
    RegisterScreen(
        nameState = MutableStateFlow(""),
        usernameState = MutableStateFlow(""),
        emailState = MutableStateFlow(""),
        companyState = MutableStateFlow(""),
        passwordState = MutableStateFlow(""),
        registerState = MutableStateFlow(ResultData.Idle()),
        usernameAvailabilityState = MutableStateFlow(UsernameAvailability.UNKNOWN),
        passwordValidationState = MutableStateFlow(PasswordValidator.validate("")),
        canRegisterState = MutableStateFlow(false),
        termsAcceptedState = MutableStateFlow(false),
        legalDocumentState = MutableStateFlow(LegalDocumentState.Hidden),
        nameChanged = {},
        usernameChanged = {},
        companyChanged = {},
        passwordChanged = {},
        termsAcceptedChanged = {},
        onOpenLegalDocument = {},
        onCloseLegalDocument = {},
        onRegisterRequest = {},
        onRegisterSuccess = {},
        navigateBack = {}
    )
}
