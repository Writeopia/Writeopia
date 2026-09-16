package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

@Serializable
data class DesktopAppVersionResponse(
    val version: String
)
