package io.writeopia.account.viewmodel

import io.writeopia.common.utils.ResultData
import kotlinx.coroutines.flow.StateFlow

interface AccountMenuViewModel {

    val isLoggedIn: StateFlow<ResultData<Boolean>>

    fun logout(onLogOutSuccess: () -> Unit)
}
