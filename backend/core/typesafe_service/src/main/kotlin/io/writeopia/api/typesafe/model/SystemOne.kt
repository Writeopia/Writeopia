package io.writeopia.api.typesafe.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The wire format of the TypeSafe System One API (`POST /v1/systemone`).
 * See https://docs.typesafe.ai/api
 */
@Serializable
data class SystemOneQuestion(
    /** "noul", "choice" or "score". */
    val type: String,
    val instructions: JsonElement,
    val criteria: JsonElement? = null,
) {
    companion object {
        /** A yes/no judgment. The answer is the probability of yes. */
        fun noul(instructions: String, whenTrue: String? = null, whenFalse: String? = null): SystemOneQuestion =
            SystemOneQuestion(
                type = NOUL,
                instructions = JsonPrimitive(instructions),
                criteria = if (whenTrue == null && whenFalse == null) {
                    null
                } else {
                    JsonObject(
                        buildMap {
                            whenTrue?.let { put("true", JsonPrimitive(it)) }
                            whenFalse?.let { put("false", JsonPrimitive(it)) }
                        }
                    )
                }
            )

        const val NOUL = "noul"
        const val CHOICE = "choice"
        const val SCORE = "score"
    }
}

@Serializable
data class SystemOneRequest(
    val state: JsonElement,
    val model: String,
    val questions: Map<String, SystemOneQuestion>,
)

@Serializable
data class SystemOneAnswer(
    val type: String,
    /** The probability of yes, for a noul. */
    val noul: Double? = null,
    val choice: String? = null,
    val score: Double? = null,
    val confidence: Double? = null,
    val probabilities: Map<String, Double>? = null,
)

@Serializable
data class SystemOneUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)

@Serializable
data class SystemOneResponse(
    val model: String? = null,
    val answers: Map<String, SystemOneAnswer> = emptyMap(),
    val usage: SystemOneUsage? = null,
)
