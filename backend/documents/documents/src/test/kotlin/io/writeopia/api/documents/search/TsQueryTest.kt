package io.writeopia.api.documents.search

import io.writeopia.databse.search.TsQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TsQueryTest {

    @Test
    fun `every word is required and matched by prefix`() {
        assertEquals("'hello':* & 'world':*", TsQuery.build("hello world"))
    }

    @Test
    fun `operators and quotes are dropped, so the query can't break`() {
        assertEquals("'foo':* & 'bar':*", TsQuery.build("foo:*&|!() 'bar\""))
        assertEquals("'a':* & 'b':* & 'com':*", TsQuery.build("a@b.com"))
    }

    @Test
    fun `letters and digits of any alphabet are kept`() {
        assertEquals("'café':* & 'über':* & '日本':* & '42':*", TsQuery.build("café über 日本 42"))
    }

    @Test
    fun `nothing to search when there is no letter or digit`() {
        assertNull(TsQuery.build(""))
        assertNull(TsQuery.build("   "))
        assertNull(TsQuery.build("!!! --- ***"))
    }

    @Test
    fun `the number and the length of the tokens are capped`() {
        val many = (1..20).joinToString(" ") { "w$it" }
        assertEquals(TsQuery.MAX_TOKENS, TsQuery.tokens(many).size)
        assertEquals(TsQuery.MAX_TOKEN_LENGTH, TsQuery.tokens("a".repeat(500)).single().length)
    }
}
