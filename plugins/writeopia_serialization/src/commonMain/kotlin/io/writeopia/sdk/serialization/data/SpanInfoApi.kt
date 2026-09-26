package io.writeopia.sdk.serialization.data

import kotlinx.serialization.Serializable

@Serializable
data class SpanInfoApi(
    val start: Int,
    val end: Int,
    val span: String,
    val extra: String? = null
)
