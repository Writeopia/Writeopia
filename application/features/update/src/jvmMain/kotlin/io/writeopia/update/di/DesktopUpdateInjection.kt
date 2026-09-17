package io.writeopia.update.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.core.configuration.DesktopAppVersion
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.update.DesktopUpdateChecker
import io.writeopia.update.api.DesktopUpdateApi
import io.writeopia.update.viewmodel.DesktopUpdateViewModel

object DesktopUpdateInjection {

    fun provideChecker(): DesktopUpdateChecker {
        val connection = WriteopiaConnectionInjector.singleton()

        return DesktopUpdateChecker(
            versionSource = DesktopUpdateApi(
                client = connection.httpClient(),
                baseUrl = connection.baseUrl()
            ),
            currentVersion = DesktopAppVersion.CURRENT,
            downloadBaseUrl = connection.baseUrl()
        )
    }

    @Composable
    fun provideViewModel(): DesktopUpdateViewModel =
        viewModel {
            DesktopUpdateViewModel(provideChecker())
        }
}
