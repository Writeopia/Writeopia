package io.writeopia.sdk.serialization.data

import kotlinx.serialization.Serializable

@Serializable
data class SpanInfoApi(
    val start: Int,
    val end: Int,
    val span: String,
    val extra: String? = null
) {
    @Deprecated("Use primary constructor with extra.")
    constructor(
        start: Int,
        end: Int,
        span: String,
    ) : this(start, end, span, null)
}
