package io.writeopia.responses

import kotlin.time.Instant
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.Serializable

@Serializable
class OllamaResponse(
    val model: String? = null,
    @Serializable(with = InstantIso8601Serializer::class)
    val created_at: Instant? = null,
    val response: String? = null,
    val done: Boolean? = null,
    val done_reason: String? = null,
    val context: List<Int>? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null,
    val error: String? = null
)
