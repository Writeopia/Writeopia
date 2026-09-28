package io.writeopia.localaiconfig.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.api.LocalAiAutoConfigApi
import io.writeopia.controller.LocalAiConfigController
import io.writeopia.di.LocalAiConfigInjector
import io.writeopia.di.LocalAiInjection
import io.writeopia.localaiconfig.viewmodel.LocalAiConfigKmpViewModel
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

/**
 * Provides a session-independent [LocalAiConfigController], keyed by [userId] instead of the
 * current logged-in user - for configuring local AI before/without a session (e.g. the offline
 * space's first-run setup), where `global_shell`'s session-bound implementation isn't available.
 */
class LocalAiConfigKmpInjector(
    private val userId: String,
    private val localAiInjection: LocalAiInjection = LocalAiInjection.singleton(),
) : LocalAiConfigInjector {

    private fun provideLocalAiAutoConfigApi(): LocalAiAutoConfigApi {
        val connectionInjector = WriteopiaConnectionInjector.singleton()
        return LocalAiAutoConfigApi(connectionInjector.httpClient(), connectionInjector.baseUrl())
    }

    @Composable
    override fun provideLocalAiConfigController(): LocalAiConfigController =
        viewModel {
            LocalAiConfigKmpViewModel(
                userId = userId,
                localAiRepository = localAiInjection.provideRepository(),
                localAiAutoConfigApi = provideLocalAiAutoConfigApi(),
            )
        }
}
