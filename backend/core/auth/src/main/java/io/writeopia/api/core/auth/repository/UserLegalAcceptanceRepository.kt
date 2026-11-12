package io.writeopia.api.core.auth.repository

import io.writeopia.api.core.auth.legal.LegalDocuments
import io.writeopia.sql.User_legal_acceptance
import io.writeopia.sql.WriteopiaDbBackend

/**
 * Records that [userId] accepted the Terms of Service and the Privacy Policy at [version].
 * Call it inside the transaction that creates the user.
 */
fun WriteopiaDbBackend.recordLegalAcceptance(
    userId: String,
    version: String,
    acceptedAt: Long = System.currentTimeMillis(),
) {
    listOf(LegalDocuments.TERMS, LegalDocuments.PRIVACY).forEach { type ->
        userLegalAcceptanceQueries.insert(userId, type, version, acceptedAt)
    }
}

fun WriteopiaDbBackend.legalAcceptancesOf(userId: String): List<User_legal_acceptance> =
    userLegalAcceptanceQueries.selectByUser(userId).executeAsList()
