package io.writeopia.sdk.models.workspace

import kotlinx.datetime.Instant

data class Workspace(
    val id: String,
    val userId: String,
    val name: String,
    val lastSync: Instant,
    val selected: Boolean,
    val role: String
) {
    companion object {
        fun disconnectedWorkspace() = Workspace(
            id = "disconnected_workspace",
            userId = "disconnected_user",
            name = "Offline workspace",
            lastSync = Instant.Companion.DISTANT_PAST,
            selected = true,
            role = Role.ADMIN.value
        )
    }
}
