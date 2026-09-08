package io.writeopia.analytics.di

import io.writeopia.analytics.AnalyticsManager
import io.writeopia.analytics.PostHogHttpAnalytics
import io.writeopia.di.AppConnectionInjection

actual class AnalyticsInjection private constructor() {

    private val analyticsManager: AnalyticsManager by lazy {
        PostHogHttpAnalytics(AppConnectionInjection.singleton().provideHttpClient())
    }

    actual fun provideAnalyticsManager(): AnalyticsManager = analyticsManager

    actual companion object {
        private var instance: AnalyticsInjection? = null

        actual fun singleton(): AnalyticsInjection = instance ?: AnalyticsInjection().also { instance = it }
    }
}
