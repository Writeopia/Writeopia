package io.writeopia.global.shell.di

import androidx.compose.runtime.Composable
import io.writeopia.global.shell.viewmodel.GlobalShellViewModel
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.flow.Flow

interface SideMenuInjector {

    @Composable
    fun provideSideMenuViewModel(keyboardEventFlow: Flow<KeyboardEvent>? = null): GlobalShellViewModel
}
