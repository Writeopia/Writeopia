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

val writeopiaJson: Json = Json {
    serializersModule = writeopiaSerializersModule()
}

val writeopiaJsonPretty: Json = Json {
    serializersModule = writeopiaSerializersModule()
    prettyPrint = true
}
