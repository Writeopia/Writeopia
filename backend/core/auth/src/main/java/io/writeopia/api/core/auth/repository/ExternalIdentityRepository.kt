@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.repository

import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

const val PROVIDER_GOOGLE = "GOOGLE"

fun WriteopiaDbBackend.getUserIdByExternalIdentity(provider: String, subject: String): String? =
    this.externalIdentityQueries
        .selectUserIdByProviderAndSubject(provider, subject)
        .executeAsOneOrNull()

fun WriteopiaDbBackend.insertExternalIdentity(provider: String, subject: String, userId: String) {
    this.externalIdentityQueries.insertExternalIdentity(
        provider = provider,
        subject = subject,
        user_id = userId,
        created_at = Clock.System.now().toEpochMilliseconds(),
    )
}

fun WriteopiaDbBackend.usernameExists(username: String): Boolean =
    this.userEntityQueries
        .usernameExists(username)
        .executeAsOne()
