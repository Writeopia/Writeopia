package io.writeopia.analytics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val analyticsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Fire-and-forget variant of [AnalyticsManager.track], for call sites that aren't
 * already inside a coroutine (e.g. plain NavController extension functions).
 */
fun AnalyticsManager.trackAsync(event: String, properties: Map<String, Any?> = emptyMap()) {
    analyticsScope.launch {
        track(event, properties)
    }
}
