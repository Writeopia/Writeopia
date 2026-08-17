package io.writeopia.api.billing.models

import kotlinx.serialization.Serializable

@Serializable
data class CheckoutSessionRequest(
    val successUrl: String? = null,
    val cancelUrl: String? = null
)

@Serializable
data class CheckoutSessionResponse(
    val checkoutUrl: String
)

@Serializable
data class PortalSessionResponse(
    val portalUrl: String
)

@Serializable
data class SubscriptionResponse(
    val planId: String,
    val status: String,
    val currentPeriodStart: Long?,
    val currentPeriodEnd: Long?,
    val cancelAtPeriodEnd: Boolean
)

@Serializable
data class SubscriptionData(
    val userId: String,
    val stripeSubscriptionId: String?,
    val planId: String,
    val status: String,
    val currentPeriodStart: Long?,
    val currentPeriodEnd: Long?,
    val cancelAtPeriodEnd: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class StripeCustomerData(
    val userId: String,
    val stripeCustomerId: String,
    val createdAt: Long
)
