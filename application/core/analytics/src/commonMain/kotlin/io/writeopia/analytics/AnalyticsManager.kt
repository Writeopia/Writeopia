package io.writeopia.analytics

interface AnalyticsManager {

    suspend fun track(event: String, properties: Map<String, Any?> = emptyMap())

    suspend fun identify(distinctId: String, properties: Map<String, Any?> = emptyMap())

    fun reset()
}
