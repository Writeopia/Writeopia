package io.writeopia.analytics

object NoOpAnalyticsManager : AnalyticsManager {

    override suspend fun track(event: String, properties: Map<String, Any?>) {}

    override suspend fun identify(distinctId: String, properties: Map<String, Any?>) {}

    override fun reset() {}
}
