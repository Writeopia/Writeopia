package io.writeopia.di

import androidx.compose.runtime.Composable
import io.writeopia.controller.LocalAiConfigController

interface LocalAiConfigInjector {

    @Composable
    fun provideLocalAiConfigController(): LocalAiConfigController
}
