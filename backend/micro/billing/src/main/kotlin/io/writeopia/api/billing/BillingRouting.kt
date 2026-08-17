package io.writeopia.api.billing

import com.stripe.model.checkout.Session
import com.stripe.model.Subscription
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.api.billing.models.CheckoutSessionRequest
import io.writeopia.api.billing.models.CheckoutSessionResponse
import io.writeopia.api.billing.models.PortalSessionResponse
import io.writeopia.api.billing.models.SubscriptionResponse
import io.writeopia.api.core.auth.repository.getUserById
import io.writeopia.api.core.auth.repository.updateUserTier
import io.writeopia.api.core.auth.routing.getUserId
import io.writeopia.connection.logger
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sql.WriteopiaDbBackend

fun Routing.billingRoutes(writeopiaDb: WriteopiaDbBackend, debugMode: Boolean = false) {
    // Create checkout session for subscription
    authenticate("auth-jwt", optional = debugMode) {
        post("/api/billing/checkout-session") {
            val userId = getUserId()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                return@post
            }

            val user = writeopiaDb.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@post
            }

            try {
                val request = try {
                    call.receive<CheckoutSessionRequest>()
                } catch (e: Exception) {
                    CheckoutSessionRequest()
                }

                // Get or create Stripe customer
                val customerId = getOrCreateStripeCustomer(writeopiaDb, userId, user.email)

                // Create checkout session
                val session = StripeService.createCheckoutSession(
                    customerId = customerId,
                    userId = userId,
                    successUrl = request.successUrl,
                    cancelUrl = request.cancelUrl
                )

                call.respond(CheckoutSessionResponse(checkoutUrl = session.url))
            } catch (e: Exception) {
                logger.error("Error creating checkout session: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to create checkout session")
            }
        }
    }

    // Create portal session for subscription management
    authenticate("auth-jwt", optional = debugMode) {
        post("/api/billing/portal-session") {
            val userId = getUserId()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                return@post
            }

            try {
                val stripeCustomer = writeopiaDb.getStripeCustomer(userId)
                if (stripeCustomer == null) {
                    call.respond(HttpStatusCode.NotFound, "No subscription found")
                    return@post
                }

                val portalSession = StripeService.createPortalSession(stripeCustomer.stripeCustomerId)
                call.respond(PortalSessionResponse(portalUrl = portalSession.url))
            } catch (e: Exception) {
                logger.error("Error creating portal session: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to create portal session")
            }
        }
    }

    // Get current subscription status
    authenticate("auth-jwt", optional = debugMode) {
        get("/api/billing/subscription") {
            val userId = getUserId()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                return@get
            }

            try {
                val subscription = writeopiaDb.getSubscription(userId)

                if (subscription != null) {
                    call.respond(
                        SubscriptionResponse(
                            planId = subscription.planId,
                            status = subscription.status,
                            currentPeriodStart = subscription.currentPeriodStart,
                            currentPeriodEnd = subscription.currentPeriodEnd,
                            cancelAtPeriodEnd = subscription.cancelAtPeriodEnd
                        )
                    )
                } else {
                    // Return free tier if no subscription exists
                    call.respond(
                        SubscriptionResponse(
                            planId = "free",
                            status = "active",
                            currentPeriodStart = null,
                            currentPeriodEnd = null,
                            cancelAtPeriodEnd = false
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error("Error getting subscription: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to get subscription")
            }
        }
    }

    // Stripe webhook endpoint
    post("/api/stripe/webhook") {
        try {
            val payload = call.receiveText()
            val sigHeader = call.request.header("Stripe-Signature")

            if (sigHeader == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing Stripe-Signature header")
                return@post
            }

            val event = StripeService.constructWebhookEvent(payload, sigHeader)
            logger.info("Received Stripe webhook event: ${event.type}")

            when (event.type) {
                "checkout.session.completed" -> {
                    val session = event.dataObjectDeserializer.`object`.orElse(null) as? Session
                    if (session != null) {
                        handleCheckoutSessionCompleted(writeopiaDb, session)
                    }
                }

                "customer.subscription.updated" -> {
                    val subscription = event.dataObjectDeserializer.`object`.orElse(null) as? Subscription
                    if (subscription != null) {
                        handleSubscriptionUpdated(writeopiaDb, subscription)
                    }
                }

                "customer.subscription.deleted" -> {
                    val subscription = event.dataObjectDeserializer.`object`.orElse(null) as? Subscription
                    if (subscription != null) {
                        handleSubscriptionDeleted(writeopiaDb, subscription)
                    }
                }

                else -> {
                    logger.info("Unhandled event type: ${event.type}")
                }
            }

            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            logger.error("Error processing webhook: ${e.message}", e)
            call.respond(HttpStatusCode.BadRequest, "Webhook error: ${e.message}")
        }
    }

    // Health check
    get("/api/billing/health") {
        call.respond(HttpStatusCode.OK, "OK")
    }
}

private fun getOrCreateStripeCustomer(
    writeopiaDb: WriteopiaDbBackend,
    userId: String,
    email: String
): String {
    val existingCustomer = writeopiaDb.getStripeCustomer(userId)
    if (existingCustomer != null) {
        return existingCustomer.stripeCustomerId
    }

    val customer = StripeService.createCustomer(email, userId)
    writeopiaDb.insertStripeCustomer(userId, customer.id)
    return customer.id
}

private fun handleCheckoutSessionCompleted(writeopiaDb: WriteopiaDbBackend, session: Session) {
    val userId = session.clientReferenceId
    if (userId == null) {
        logger.warn("Checkout session completed without client reference ID")
        return
    }

    val subscriptionId = session.subscription
    logger.info("Checkout completed for user $userId with subscription $subscriptionId")

    // Get subscription details from Stripe
    if (subscriptionId != null) {
        val subscription = StripeService.getSubscription(subscriptionId)

        writeopiaDb.insertOrUpdateSubscription(
            userId = userId,
            stripeSubscriptionId = subscriptionId,
            planId = "premium",
            status = subscription.status,
            currentPeriodStart = subscription.currentPeriodStart,
            currentPeriodEnd = subscription.currentPeriodEnd,
            cancelAtPeriodEnd = subscription.cancelAtPeriodEnd
        )

        // Update user tier to PREMIUM
        writeopiaDb.updateUserTier(userId, Tier.PREMIUM)
        logger.info("User $userId upgraded to PREMIUM tier")
    }
}

private fun handleSubscriptionUpdated(writeopiaDb: WriteopiaDbBackend, subscription: Subscription) {
    val customerId = subscription.customer
    val stripeCustomer = findUserByStripeCustomer(writeopiaDb, customerId)

    if (stripeCustomer == null) {
        logger.warn("Subscription updated for unknown customer: $customerId")
        return
    }

    val userId = stripeCustomer.userId
    logger.info("Subscription updated for user $userId: status=${subscription.status}")

    writeopiaDb.insertOrUpdateSubscription(
        userId = userId,
        stripeSubscriptionId = subscription.id,
        planId = if (subscription.status == "active") "premium" else "free",
        status = subscription.status,
        currentPeriodStart = subscription.currentPeriodStart,
        currentPeriodEnd = subscription.currentPeriodEnd,
        cancelAtPeriodEnd = subscription.cancelAtPeriodEnd
    )

    // Update user tier based on subscription status
    val tier = if (subscription.status == "active") Tier.PREMIUM else Tier.FREE
    writeopiaDb.updateUserTier(userId, tier)
}

private fun handleSubscriptionDeleted(writeopiaDb: WriteopiaDbBackend, subscription: Subscription) {
    val customerId = subscription.customer
    val stripeCustomer = findUserByStripeCustomer(writeopiaDb, customerId)

    if (stripeCustomer == null) {
        logger.warn("Subscription deleted for unknown customer: $customerId")
        return
    }

    val userId = stripeCustomer.userId
    logger.info("Subscription deleted for user $userId")

    writeopiaDb.cancelSubscription(userId)
    writeopiaDb.updateUserTier(userId, Tier.FREE)
    logger.info("User $userId downgraded to FREE tier")
}

private fun findUserByStripeCustomer(
    writeopiaDb: WriteopiaDbBackend,
    stripeCustomerId: String
): io.writeopia.api.billing.models.StripeCustomerData? {
    return writeopiaDb.getUserByStripeCustomerId(stripeCustomerId)
}
