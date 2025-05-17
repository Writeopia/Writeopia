package io.writeopia.account.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.account.viewmodel.AccountMenuKmpViewModel
import io.writeopia.account.viewmodel.AccountMenuViewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo

class AccountMenuKmpInjector private constructor() {

    private fun provideAccountMenuKmpViewModel(): AccountMenuKmpViewModel =
        AccountMenuKmpViewModel(
            authManager = AuthCoreInjectionNeo.singleton().provideAuthRepository(),
            authRepository = AuthCoreInjectionNeo.singleton().provideAuthRepository()
        )

    @Composable
    fun provideAccountMenuViewModel(): AccountMenuViewModel =
        viewModel { provideAccountMenuKmpViewModel() }

    companion object {
        private var instance: AccountMenuKmpInjector? = null

        fun singleton(): AccountMenuKmpInjector = instance ?: AccountMenuKmpInjector().also {
            instance = it
        }
    }
}
