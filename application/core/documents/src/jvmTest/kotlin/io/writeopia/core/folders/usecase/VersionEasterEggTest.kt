package io.writeopia.core.folders.usecase

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.tutorials.EasterEggs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionEasterEggTest {

    @Test
    fun theDocumentIsAboutTheWriterOfTheVersion() {
        val document = writeopiaJson
            .decodeFromString<DocumentApi>(EasterEggs.versionWriterDocument(GenerateId::generate))
            .toModel()

        assertEquals("Manuel Bandeira", document.title)
        assertTrue(document.content.size > 5)
        assertEquals(document.content.size, document.content.values.map { it.id }.toSet().size)
    }
}
