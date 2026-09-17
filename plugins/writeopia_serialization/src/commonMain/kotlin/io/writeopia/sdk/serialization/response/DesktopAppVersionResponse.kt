package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

/**
 * Response returned by the public desktop application version endpoint.
 *
 * @property version Latest published desktop application version reported by the backend.
 */
@Serializable
data class DesktopAppVersionResponse(
    val version: String
)
