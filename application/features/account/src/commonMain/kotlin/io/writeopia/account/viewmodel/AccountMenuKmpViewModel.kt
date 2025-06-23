package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.toBoolean
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class AccountMenuKmpViewModel(
    private val authRepository: AuthRepository,
) : AccountMenuViewModel, ViewModel() {
    override val isLoggedIn: StateFlow<ResultData<Boolean>> by lazy {
        authRepository.listenForUser().map {
            ResultData.Complete(it.id != WriteopiaUser.DISCONNECTED)
        }.stateIn(viewModelScope, SharingStarted.Lazily, ResultData.Loading())
    }

    override fun logout(onLogOutSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = authRepository.logout()

            if (result.toBoolean()) {
                onLogOutSuccess()
            }
        }
    }
}
