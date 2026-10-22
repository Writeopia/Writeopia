package io.writeopia.sdk.serialization.serializers

import io.writeopia.sdk.serialization.data.StoryStepApi
import io.writeopia.sdk.serialization.data.StoryTypeApi
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sdk.utils.iterables.Rank
import kotlin.test.Test
import kotlin.test.assertEquals

class RankSerializerTest {

    private val type = """{"name":"message","number":0}"""

    @Test
    fun `a rank string is read as it is`() {
        val step = writeopiaJson.decodeFromString<StoryStepApi>(
            """{"id":"a","type":$type,"position":"0001000003"}"""
        )

        assertEquals("0001000003", step.position)
    }

    @Test
    fun `a numeric position from an old document is read as the rank at that number`() {
        val step = writeopiaJson.decodeFromString<StoryStepApi>(
            """{"id":"a","type":$type,"position":2}"""
        )

        assertEquals(Rank.at(2), step.position)
    }

    @Test
    fun `a fractional position from an old document is read as the rank at its integer part`() {
        val step = writeopiaJson.decodeFromString<StoryStepApi>(
            """{"id":"a","type":$type,"position":20000.0}"""
        )

        assertEquals(Rank.at(20000), step.position)
    }

    @Test
    fun `a missing position is the first rank`() {
        val step = writeopiaJson.decodeFromString<StoryStepApi>("""{"id":"a","type":$type}""")

        assertEquals(Rank.FIRST, step.position)
    }

    @Test
    fun `the position is written as a string`() {
        val json = writeopiaJson.encodeToString(
            StoryStepApi.serializer(),
            StoryStepApi(id = "a", type = StoryTypeApi("message", 0), position = Rank.at(1))
        )

        assertEquals("""{"id":"a","type":{"name":"message","number":0},"position":"${Rank.at(1)}"}""", json)
    }
}
