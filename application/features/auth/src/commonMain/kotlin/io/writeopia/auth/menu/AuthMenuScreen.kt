package io.writeopia.auth.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.auth.utils.loginScreen
import io.writeopia.common.utils.ResultData
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun AuthMenuScreen(
    modifier: Modifier = Modifier,
    isConnectedState: StateFlow<ResultData<Boolean>>,
    saveUserChoiceOffline: () -> Unit,
    navigateToLogin: () -> Unit,
    navigateToRegister: () -> Unit,
    navigateToApp: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val isConnected = isConnectedState.collectAsState().value) {
            is ResultData.Complete -> {
                if (isConnected.data) {
                    println("complete! connected")
                    LaunchedEffect("navigateUp") {
                        navigateToApp()
                    }
                } else {
                    println("complete! auth")
                    AuthMenuContentScreen(
                        navigateToLogin,
                        navigateToRegister,
                        navigateToApp,
                        saveUserChoiceOffline
                    )
                }
            }

            is ResultData.Error -> {
                println("error")
                AuthMenuContentScreen(
                    navigateToLogin,
                    navigateToRegister,
                    navigateToApp,
                    saveUserChoiceOffline
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
    navigateToLogin: () -> Unit,
    navigateToRegister: () -> Unit,
    saveUserChoiceOffline: () -> Unit,
    navigateToApp: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
//        Image(
//            modifier = Modifier.fillMaxWidth(),
//            painter = painterResource(id = R.drawable.top_background_auth),
//            contentDescription = "",
//            contentScale = ContentScale.FillWidth,
//        )
//
//        Image(
//            modifier = Modifier.align(Alignment.BottomEnd),
//            painter = painterResource(id = R.drawable.bottom_end_corner_auth_background),
//            contentDescription = "",
//        )

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

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                "Email",
                onValueChange = {},
                shape = shape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                "Password",
                onValueChange = {},
                shape = shape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
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

@Preview
@Composable
fun AuthMenuContentScreenPreview() {
    Surface {
        AuthMenuContentScreen(
            navigateToLogin = {},
            navigateToRegister = {},
            navigateToApp = {},
            saveUserChoiceOffline = {}
        )
    }
}
