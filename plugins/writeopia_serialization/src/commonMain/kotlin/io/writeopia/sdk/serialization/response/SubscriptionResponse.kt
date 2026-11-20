package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

/**
 * The subscription of the signed-in user, asked by the apps right after signing in or signing up:
 * a user who is not [premium] sees the paywall.
 *
 * @property premium Whether the user can use the cloud account.
 * @property tier "FREE" or "PREMIUM".
 * @property monthlyPriceCents Price of the monthly subscription, in the minor unit of [currency].
 * @property currency ISO 4217 code of the price, such as "USD".
 */
@Serializable
data class SubscriptionResponse(
    val premium: Boolean,
    val tier: String,
    val monthlyPriceCents: Long,
    val currency: String,
)
