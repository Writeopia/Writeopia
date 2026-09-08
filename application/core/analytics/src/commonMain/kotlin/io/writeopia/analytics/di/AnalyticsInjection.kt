package io.writeopia.analytics.di

import io.writeopia.analytics.AnalyticsManager

expect class AnalyticsInjection {

    fun provideAnalyticsManager(): AnalyticsManager

    companion object {
        fun singleton(): AnalyticsInjection
    }
}
