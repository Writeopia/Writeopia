package io.writeopia.account.viewmodel

import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.StateFlow

interface AccountMenuViewModel {

    val isLoggedIn: StateFlow<ResultData<Boolean>>

    val lastWorkspaceSync: StateFlow<String>

    fun logout(onLogOutSuccess: () -> Unit)

    fun syncWorkspace()
}
