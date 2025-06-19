package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.toBoolean
import io.writeopia.sdk.models.user.WriteopiaUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
