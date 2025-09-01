package io.writeopia.sdk.serialization.data

import kotlinx.serialization.Serializable

@Serializable
data class IconApi(val label: String, val tint: Long?)
