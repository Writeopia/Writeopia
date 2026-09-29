package io.writeopia.sdk.persistence.parse

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.persistence.utils.imageGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class StoryUnitParseKtTest {

    @Test
    fun `story step timestamp should survive room mapping round trip`() {
        val step = StoryStep(
            id = "step-1",
            type = StoryTypes.TEXT.type,
            text = "text",
            lastUpdatedAt = 123L,
        )

        val entity = step.toEntity(0.0, "document-1")
        val restored = entity.toModel()

        assertEquals(123L, entity.lastUpdatedAt)
        assertEquals(123L, restored.lastUpdatedAt)
    }

    @Test
    fun `nested story steps should flatten recursively for incremental room save`() {
        val grandchild = StoryStep(
            id = "grandchild",
            type = StoryTypes.TEXT.type,
            text = "grandchild",
        )
        val child = StoryStep(
            id = "child",
            type = StoryTypes.TEXT.type,
            text = "child",
            steps = listOf(grandchild),
        )
        val parent = StoryStep(
            id = "parent",
            type = StoryTypes.TEXT.type,
            text = "parent",
            steps = listOf(child),
        )

        val entities = mapOf(0.0 to parent).toEntity("document-1")
        val byId = entities.associateBy { entity -> entity.id }

        assertEquals(setOf("parent", "child", "grandchild"), byId.keys)
        assertEquals("parent", byId.getValue("child").parentId)
        assertEquals("child", byId.getValue("grandchild").parentId)
    }

    @Test
    fun `parsing a group of image`() {
        val id = GenerateId.generate()
        val entity = imageGroup().toEntity(id)

        assertEquals("group_image", entity.first().type)

        entity.forEachIndexed { i, entityUnit ->
            assertEquals(id, entityUnit.documentId, "step $i should have a document id")
        }

        val parentId = entity.first().id

        entity.drop(1).forEachIndexed { i, entityUnit ->
            assertEquals(parentId, entityUnit.parentId, "step ${i + 1} should have a parent id")
        }
    }
}
