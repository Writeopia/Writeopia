package io.writeopia.update.di

import io.writeopia.core.configuration.DesktopAppVersion
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.update.DesktopUpdateChecker
import io.writeopia.update.api.DesktopUpdateApi

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
}
