package io.writeopia.sdk.utils.iterables

import kotlin.random.Random

/**
 * The position of a step as a sort key that always has room between two neighbours, so no
 * step ever has to move to make room for another (fractional indexing, like LexoRank without
 * its buckets).
 *
 * A rank is an integer part of [INTEGER_WIDTH] decimal digits, zero padded, followed by an
 * optional fraction in base 36 (`0-9a-z`) with no trailing `0`. Ranks compare as plain strings:
 * digits and lowercase letters sort the same under byte order and under the locale collations
 * of PostgreSQL, so `ORDER BY position` agrees with the clients everywhere.
 *
 * - A step written after the last one gets the next integer: `0001000000`, `0001000001`...
 * - A step added between two others gets the midpoint of their fractions, one more character
 *   when the fractions are adjacent: between `0001000000` and `0001000001` comes
 *   `0001000000i`, then `0001000000r`... The key grows by about one character every five
 *   inserts at the same spot, and never needs the other steps to move.
 * - A short random suffix goes on every key made between two others, so two devices inserting
 *   between the same steps don't produce the same key.
 */
object Rank {
    private const val INTEGER_WIDTH = 10
    private const val FIRST_INTEGER = 1_000_000L
    private const val MAX_INTEGER = 9_999_999_999L
    private const val DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz"
    private const val JITTER_LENGTH = 2

    /** The position of the first step of a new document. */
    const val FIRST = "0001000000"

    /** Below every rank; never a rank itself (the integer part always has [INTEGER_WIDTH] digits). */
    const val NONE = ""

    /**
     * Whether keys made between two others get a random suffix. On by default; tests that
     * assert exact keys turn it off.
     */
    var jitter: Boolean = true

    /** The rank of the [index]th step of a document written in order: [FIRST] for 0. */
    fun at(index: Int): String = integer(FIRST_INTEGER + index)

    /** The rank of the [index]th step of a document written in order: [FIRST] for 0. */
    fun at(index: Long): String = integer(FIRST_INTEGER + index)

    /** The rank right after [rank]: the next integer. */
    fun after(rank: String): String {
        val next = integerPart(rank) + 1
        return if (next <= MAX_INTEGER) integer(next) else integer(integerPart(rank)) + midpoint(fractionPart(rank), null)
    }

    /**
     * The rank right before [rank]: its integer alone when it has a fraction, otherwise the
     * previous integer.
     */
    fun before(rank: String): String {
        val integer = integerPart(rank)
        return when {
            fractionPart(rank).isNotEmpty() -> integer(integer)
            integer > 0 -> integer(integer - 1)
            else -> error("No rank before $rank")
        }
    }

    /**
     * A rank strictly between [after] and [before] (null for no bound on that side). When the
     * integers leave room, the integer right after [after]; otherwise the midpoint of the
     * fractions, with a random suffix when [jitter] is on.
     */
    fun between(after: String?, before: String?): String {
        if (after == null && before == null) return FIRST
        if (after == null) return before(before!!)
        if (before == null) return after(after)
        require(after < before) { "Ranks out of order: $after >= $before" }

        val integerAfter = integerPart(after)
        val integerBefore = integerPart(before)
        val key = when {
            integerBefore > integerAfter + 1 -> integer(integerAfter + 1)
            integerBefore == integerAfter + 1 -> integer(integerAfter) + midpoint(fractionPart(after), null)
            else -> integer(integerAfter) + midpoint(fractionPart(after), fractionPart(before))
        }
        return jittered(key, before)
    }

    /** [count] ranks in order, each between the one before it and [before]. */
    fun between(after: String?, before: String?, count: Int): List<String> {
        var previous = after
        return List(count) {
            val key = between(previous, before)
            previous = key
            key
        }
    }

    private fun jittered(key: String, before: String): String {
        if (!jitter) return key
        val suffix = buildString {
            repeat(JITTER_LENGTH - 1) { append(DIGITS[Random.nextInt(DIGITS.length)]) }
            append(DIGITS[1 + Random.nextInt(DIGITS.length - 1)]) // never a trailing 0
        }
        val candidate = key + suffix
        return if (candidate < before) candidate else key
    }

    private fun integerPart(rank: String): Long = rank.take(INTEGER_WIDTH).toLong()

    private fun fractionPart(rank: String): String = rank.drop(INTEGER_WIDTH)

    private fun integer(value: Long): String {
        require(value in 0..MAX_INTEGER) { "Rank integer out of range: $value" }
        return value.toString().padStart(INTEGER_WIDTH, '0')
    }

    /**
     * A fraction strictly between [a] and [b] (null for the top), both without trailing `0`.
     * The classic fractional-indexing midpoint over base-36 digits.
     */
    private fun midpoint(a: String, b: String?): String {
        if (b != null) {
            require(a < b) { "Fractions out of order: $a >= $b" }
            var common = 0
            while (common < b.length && (a.getOrNull(common) ?: '0') == b[common]) common++
            if (common > 0) return b.take(common) + midpoint(a.drop(common), b.drop(common))
        }

        val digitA = a.firstOrNull()?.let(DIGITS::indexOf) ?: 0
        val digitB = b?.first()?.let(DIGITS::indexOf) ?: DIGITS.length
        return if (digitB - digitA > 1) {
            DIGITS[(digitA + digitB) / 2].toString()
        } else if (b != null && b.length > 1) {
            b.take(1)
        } else {
            DIGITS[digitA] + midpoint(a.drop(1), null)
        }
    }
}
