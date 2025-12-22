package io.writeopia.responses

import kotlinx.serialization.Serializable

@Serializable
class OllamaResponse(
    val model: String? = null,
//    val created_at: Long? = null,
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
