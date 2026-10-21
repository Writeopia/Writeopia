package io.writeopia.api.embeddings

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmbeddingVectorsTest {

    @Test
    fun `pgvector text round trips`() {
        val vector = floatArrayOf(0.5f, -1.25f, 3e-5f, 0f)

        val text = EmbeddingVectors.toPgText(vector)

        assertTrue(text.startsWith("[") && text.endsWith("]"))
        assertContentEquals(vector, EmbeddingVectors.fromPgText(text))
        assertContentEquals(floatArrayOf(1f, 2f), EmbeddingVectors.fromPgText(" [ 1 , 2 ] "))
    }

    @Test
    fun `the mean averages each dimension`() {
        val mean = EmbeddingVectors.mean(listOf(floatArrayOf(1f, 0f), floatArrayOf(3f, 2f)))

        assertContentEquals(floatArrayOf(2f, 1f), mean)
        assertNull(EmbeddingVectors.mean(emptyList()))
    }

    @Test
    fun `the weighted mean of a folder tree equals the mean over all its documents`() {
        val docs = listOf(floatArrayOf(1f, 1f), floatArrayOf(3f, 1f), floatArrayOf(5f, 7f))
        val childMean = EmbeddingVectors.mean(docs.drop(1))!!

        val weighted = EmbeddingVectors.weightedMean(listOf(docs[0] to 1, childMean to 2))!!
        val flat = EmbeddingVectors.mean(docs)!!

        assertContentEquals(flat, weighted)
    }

    @Test
    fun `entries without documents are ignored`() {
        val mean = EmbeddingVectors.weightedMean(listOf(floatArrayOf(2f) to 1, floatArrayOf(100f) to 0))

        assertContentEquals(floatArrayOf(2f), mean)
        assertNull(EmbeddingVectors.weightedMean(listOf(floatArrayOf(2f) to 0)))
    }

    @Test
    fun `cosine similarity`() {
        assertEquals(1L, EmbeddingVectors.cosine(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 1e-6)
        assertEquals(0L, EmbeddingVectors.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6)
        assertEquals(0L, EmbeddingVectors.cosine(floatArrayOf(0f, 0f), floatArrayOf(0f, 1f)), 1e-6)
    }
}
