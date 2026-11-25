package io.writeopia.sdk.serialization.json

import io.writeopia.sdk.serialization.serializers.InstantSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun writeopiaSerializersModule(): SerializersModule = SerializersModule {
    contextual(InstantSerializer)
}

// Unknown keys are ignored so a client keeps decoding what a newer backend adds to a response.
val writeopiaJson: Json = Json {
    serializersModule = writeopiaSerializersModule()
    ignoreUnknownKeys = true
}

val writeopiaJsonPretty: Json = Json {
    serializersModule = writeopiaSerializersModule()
    ignoreUnknownKeys = true
    prettyPrint = true
}
