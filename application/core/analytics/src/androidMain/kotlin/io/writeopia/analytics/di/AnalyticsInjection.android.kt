package io.writeopia.analytics.di

import android.content.Context
import com.posthog.PostHogAndroidConfig
import com.posthog.android.PostHog
import com.posthog.android.PostHogAndroid
import io.writeopia.analytics.AnalyticsManager
import io.writeopia.analytics.PostHogConfig

actual class AnalyticsInjection private constructor() {

    actual fun provideAnalyticsManager(): AnalyticsManager = PostHogAndroidAnalytics

    actual companion object {
        private var instance: AnalyticsInjection? = null

        actual fun singleton(): AnalyticsInjection = instance ?: AnalyticsInjection().also { instance = it }

        fun initialize(context: Context) {
            val config = PostHogAndroidConfig(apiKey = PostHogConfig.API_KEY, host = PostHogConfig.HOST)
            PostHogAndroid.setup(context, config)
        }
    }
}

private object PostHogAndroidAnalytics : AnalyticsManager {

    override suspend fun track(event: String, properties: Map<String, Any?>) {
        PostHog.capture(event, properties = properties)
    }

    override suspend fun identify(distinctId: String, properties: Map<String, Any?>) {
        PostHog.identify(distinctId, userProperties = properties)
    }

    override fun reset() {
        PostHog.reset()
    }
}
