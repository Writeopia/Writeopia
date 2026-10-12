package io.writeopia.api.embeddings

import kotlin.math.sqrt

/** Arithmetic on embedding vectors and the text form pgvector reads and writes (`[0.1,0.2,...]`). */
object EmbeddingVectors {

    fun toPgText(vector: FloatArray): String = vector.joinToString(separator = ",", prefix = "[", postfix = "]")

    fun fromPgText(text: String): FloatArray =
        text.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .filter { it.isNotBlank() }
            .map { it.trim().toFloat() }
            .toFloatArray()

    /** The element-wise mean, or null for no vectors. All vectors must have the same size. */
    fun mean(vectors: List<FloatArray>): FloatArray? =
        weightedMean(vectors.map { vector -> vector to 1 })

    /**
     * The mean of the vectors each weighted by a count, or null when the counts add up to nothing.
     * The mean of a folder tree: its documents weigh one each, a child folder's mean weighs the
     * number of documents it stands for, which gives the mean over every document in the tree.
     */
    fun weightedMean(entries: List<Pair<FloatArray, Int>>): FloatArray? {
        val weighted = entries.filter { (_, count) -> count > 0 }
        val total = weighted.sumOf { (_, count) -> count }
        if (total == 0) return null

        val size = weighted.first().first.size
        require(weighted.all { (vector, _) -> vector.size == size }) { "Vectors must have the same dimension" }

        val sum = DoubleArray(size)
        weighted.forEach { (vector, count) ->
            for (i in 0 until size) sum[i] += vector[i].toDouble() * count
        }

        return FloatArray(size) { i -> (sum[i] / total).toFloat() }
    }

    /** Cosine similarity in -1..1, 0.0 when one of the vectors is all zeros. */
    fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Vectors must have the same dimension" }
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i]
            normA += a[i].toDouble() * a[i]
            normB += b[i].toDouble() * b[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
