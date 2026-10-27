package io.writeopia.sdk.serialization.serializers

import io.writeopia.sdk.utils.iterables.Rank
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The position of a step on the wire: a rank string (see [Rank]). Documents written before
 * ranks (files exported by older versions, the tutorials, older clients) carry a number
 * instead: an index or a numeric position, in both cases ordered like the ranks of
 * [Rank.at], so a number decodes as the rank at that integer.
 */
object RankSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.writeopia.Rank", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String {
        if (decoder !is JsonDecoder) return decoder.decodeString()

        val element = decoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return Rank.FIRST
        if (primitive.isString) return primitive.content

        val number = primitive.longOrNull ?: primitive.doubleOrNull?.toLong() ?: return Rank.FIRST
        return Rank.at(number.coerceIn(0L, MAX_LEGACY_POSITION))
    }

    /** The largest numeric position a rank integer can hold ([Rank] has ten decimal digits). */
    private const val MAX_LEGACY_POSITION = 8_999_999_999L
}
