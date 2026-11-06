package io.writeopia.api.documents.search

import io.writeopia.databse.search.TsQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TsQueryTest {

    @Test
    fun `every lexeme is required and matched by prefix`() {
        assertEquals("'hello':* & 'world':*", TsQuery.build(listOf("hello", "world")))
    }

    @Test
    fun `quotes and backslashes inside a lexeme are escaped, so the query can't break`() {
        assertEquals("'it''s':* & 'a\\\\b':*", TsQuery.build(listOf("it's", "a\\b")))
    }

    @Test
    fun `nothing to search without lexemes`() {
        assertNull(TsQuery.build(emptyList()))
        assertNull(TsQuery.build(listOf("", "")))
    }

    @Test
    fun `the number and the length of the lexemes are capped`() {
        val many = (1..20).map { "w$it" }
        assertEquals(TsQuery.MAX_TOKENS, TsQuery.build(many)!!.split(" & ").size)
        assertEquals("'${"a".repeat(TsQuery.MAX_TOKEN_LENGTH)}':*", TsQuery.build(listOf("a".repeat(500))))
    }
}
