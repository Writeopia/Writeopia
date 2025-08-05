package io.writeopia.sdk.models

import kotlinx.datetime.Instant

data class Workspace(
    val id: String,
    val userId: String,
    val name: String,
    val lastSync: Instant,
    val selected: Boolean
) {
    companion object {
        fun disconnectedWorkspace() = Workspace(
            id = "disconnected_workspace",
            userId = "disconnected_user",
            name = "",
            lastSync = Instant.DISTANT_PAST,
            selected = true
        )
    }
}
