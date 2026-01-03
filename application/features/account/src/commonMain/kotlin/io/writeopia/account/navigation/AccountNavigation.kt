package io.writeopia.account.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.account.di.AccountMenuKmpInjector
import io.writeopia.account.ui.AccountMenuScreen
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.model.ColorThemeOption
import io.writeopia.resources.WrStrings

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.accountMenuNavigation(
    navigateToAuthMenu: () -> Unit,
    navigationClick: () -> Unit,
    selectColorTheme: (ColorThemeOption) -> Unit,
) {
    composable(
        Destinations.ACCOUNT.id,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { intSize -> -intSize }
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { intSize -> -intSize }
            )
        }
    ) {
        val accountMenuViewModel = AccountMenuKmpInjector.singleton().provideAccountMenuViewModel()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            WrStrings.settings(),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = navigationClick)
                                    .padding(10.dp),
                                imageVector = WrIcons.backArrowMobile,
                                contentDescription = "",
//                    stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            AccountMenuScreen(
                modifier = Modifier.padding(paddingValues),
                accountMenuViewModel = accountMenuViewModel,
                isLoggedInState = accountMenuViewModel.isLoggedIn,
                onLogout = navigateToAuthMenu,
                goToRegister = navigateToAuthMenu,
                selectColorTheme = selectColorTheme
            )
        }
    }
}
