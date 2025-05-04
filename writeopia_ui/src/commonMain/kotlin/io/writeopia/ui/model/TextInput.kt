package io.writeopia.ui.model

import io.writeopia.sdk.models.span.SpanInfo

data class TextInput(val text: String, val start: Int = 0, val end: Int = 0, val spans: Set<SpanInfo> = emptySet())
