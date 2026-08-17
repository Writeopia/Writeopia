@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.billing

import io.writeopia.api.billing.models.StripeCustomerData
import io.writeopia.api.billing.models.SubscriptionData
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun WriteopiaDbBackend.getStripeCustomer(userId: String): StripeCustomerData? =
    this.subscriptionEntityQueries
        .selectStripeCustomerByUserId(userId)
        .executeAsOneOrNull()
        ?.let { customer ->
            StripeCustomerData(
                userId = customer.user_id,
                stripeCustomerId = customer.stripe_customer_id,
                createdAt = customer.created_at
            )
        }

fun WriteopiaDbBackend.insertStripeCustomer(userId: String, stripeCustomerId: String) {
    val now = Clock.System.now().toEpochMilliseconds()
    this.subscriptionEntityQueries.insertStripeCustomer(userId, stripeCustomerId, now)
}

fun WriteopiaDbBackend.deleteStripeCustomer(userId: String) {
    this.subscriptionEntityQueries.deleteStripeCustomerByUserId(userId)
}

fun WriteopiaDbBackend.getUserByStripeCustomerId(stripeCustomerId: String): StripeCustomerData? =
    this.subscriptionEntityQueries
        .selectUserByStripeCustomerId(stripeCustomerId)
        .executeAsOneOrNull()
        ?.let { customer ->
            StripeCustomerData(
                userId = customer.user_id,
                stripeCustomerId = customer.stripe_customer_id,
                createdAt = customer.created_at
            )
        }

fun WriteopiaDbBackend.getSubscription(userId: String): SubscriptionData? =
    this.subscriptionEntityQueries
        .selectSubscriptionByUserId(userId)
        .executeAsOneOrNull()
        ?.let { subscription ->
            SubscriptionData(
                userId = subscription.user_id,
                stripeSubscriptionId = subscription.stripe_subscription_id,
                planId = subscription.plan_id,
                status = subscription.status,
                currentPeriodStart = subscription.current_period_start,
                currentPeriodEnd = subscription.current_period_end,
                cancelAtPeriodEnd = subscription.cancel_at_period_end != 0,
                createdAt = subscription.created_at,
                updatedAt = subscription.updated_at
            )
        }

fun WriteopiaDbBackend.insertOrUpdateSubscription(
    userId: String,
    stripeSubscriptionId: String?,
    planId: String,
    status: String,
    currentPeriodStart: Long?,
    currentPeriodEnd: Long?,
    cancelAtPeriodEnd: Boolean
) {
    val now = Clock.System.now().toEpochMilliseconds()
    this.subscriptionEntityQueries.insertOrUpdateSubscription(
        user_id = userId,
        stripe_subscription_id = stripeSubscriptionId,
        plan_id = planId,
        status = status,
        current_period_start = currentPeriodStart,
        current_period_end = currentPeriodEnd,
        cancel_at_period_end = if (cancelAtPeriodEnd) 1 else 0,
        created_at = now,
        updated_at = now
    )
}

fun WriteopiaDbBackend.updateSubscriptionStatus(userId: String, status: String) {
    val now = Clock.System.now().toEpochMilliseconds()
    this.subscriptionEntityQueries.updateSubscriptionStatus(status, now, userId)
}

fun WriteopiaDbBackend.updateSubscriptionPlan(
    userId: String,
    planId: String,
    stripeSubscriptionId: String?,
    status: String
) {
    val now = Clock.System.now().toEpochMilliseconds()
    this.subscriptionEntityQueries.updateSubscriptionPlan(planId, stripeSubscriptionId, status, now, userId)
}

fun WriteopiaDbBackend.cancelSubscription(userId: String) {
    val now = Clock.System.now().toEpochMilliseconds()
    this.subscriptionEntityQueries.cancelSubscription(now, userId)
}

fun WriteopiaDbBackend.deleteSubscription(userId: String) {
    this.subscriptionEntityQueries.deleteSubscriptionByUserId(userId)
}
