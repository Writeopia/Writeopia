package io.writeopia.storage

import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

/**
 * Keeps the `user_storage_usage` table: how many bytes of media each user has in the bucket.
 *
 * Every change is an optimistic lock. [usage] reads the row with its version; [reserve] and
 * [release] compute the new total from that reading and write it with `UPDATE ... WHERE
 * version = ?`. When the update changes no row, another request wrote in between: the row is
 * read again and the change is retried, up to [maxAttempts] times. Nothing is ever locked in
 * the database, so two uploads of the same user never wait for each other, yet no byte is
 * lost or counted twice.
 */
class StorageUsageService(
    private val db: WriteopiaDbBackend,
    private val quotaBytes: Long = StorageQuota.bytes(),
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    /** The current usage of [userId] with the version of its row; a user without a row has used nothing. */
    fun usage(userId: String): StorageUsage {
        val row = db.userStorageUsageEntityQueries.selectByUserId(userId).executeAsOneOrNull()
        if (row != null) return StorageUsage(userId, row.used_bytes, quotaBytes, row.version)

        db.userStorageUsageEntityQueries.insertIfAbsent(userId, now())
        val created = db.userStorageUsageEntityQueries.selectByUserId(userId).executeAsOne()
        return StorageUsage(userId, created.used_bytes, quotaBytes, created.version)
    }

    /**
     * Counts [bytes] more for [userId], unless they would overflow the quota. Call it before
     * writing the object, and [release] the same amount if the write then fails.
     */
    fun reserve(userId: String, bytes: Long): StorageChange {
        require(bytes >= 0) { "bytes must not be negative: $bytes" }

        return change(userId, bytes) { current ->
            if (current.fits(bytes)) current.usedBytes + bytes else null
        }
    }

    /** Counts [bytes] less for [userId], never below zero. */
    fun release(userId: String, bytes: Long): StorageChange {
        require(bytes >= 0) { "bytes must not be negative: $bytes" }

        return change(userId, bytes) { current -> (current.usedBytes - bytes).coerceAtLeast(0) }
    }

    /**
     * Reads the row, asks [newTotal] for the bytes to write (null when the quota refuses the
     * change), and writes them with the version read. Retries while the version moved under
     * it; [StorageChange.Conflict] when every attempt lost the race.
     */
    private fun change(userId: String, bytes: Long, newTotal: (StorageUsage) -> Long?): StorageChange {
        repeat(maxAttempts) { attempt ->
            val current = usage(userId)
            val total = newTotal(current) ?: return StorageChange.QuotaExceeded(current, bytes)

            val changed = db.userStorageUsageEntityQueries.updateIfVersion(
                used_bytes = total,
                updated_at = now(),
                user_id = userId,
                version = current.version
            ).value

            if (changed == 1L) {
                return StorageChange.Applied(current.copy(usedBytes = total, version = current.version + 1))
            }

            logger.info(
                "Storage usage of {} changed under us (version {}), attempt {}/{}",
                userId, current.version, attempt + 1, maxAttempts
            )
        }

        return StorageChange.Conflict(maxAttempts)
    }

    /** Forgets the row of [userId], when the account goes. */
    fun delete(userId: String) {
        db.userStorageUsageEntityQueries.deleteByUser(userId)
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
    }
}
