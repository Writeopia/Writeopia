@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.models.workspace

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Workspace(
    val id: String,
    val userId: String,
    val name: String,
    val lastSync: Instant,
    val lastEventSync: Long = 0L,
    val selected: Boolean,
    val role: String,
    val documentCount: Int = 0,
) {
    companion object {
        fun disconnectedWorkspace() = Workspace(
            id = "disconnected_user",
            userId = "disconnected_user",
            name = "Offline workspace",
            lastSync = Instant.DISTANT_PAST,
            lastEventSync = 0L,
            selected = true,
            role = Role.ADMIN.value,
            documentCount = 0,
        )
    }
}
