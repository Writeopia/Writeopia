package io.writeopia.api.billing

import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.model.billingportal.Session as PortalSession
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.checkout.SessionCreateParams
import com.stripe.param.billingportal.SessionCreateParams as PortalSessionCreateParams
import io.writeopia.connection.logger

object StripeService {

    private val secretKey: String by lazy {
        System.getenv("STRIPE_SECRET_KEY")
            ?: throw IllegalStateException("STRIPE_SECRET_KEY environment variable is not set")
    }

    private val webhookSecret: String by lazy {
        System.getenv("STRIPE_WEBHOOK_SECRET")
            ?: throw IllegalStateException("STRIPE_WEBHOOK_SECRET environment variable is not set")
    }

    private val priceIdPremium: String by lazy {
        System.getenv("STRIPE_PRICE_ID_PREMIUM")
            ?: throw IllegalStateException("STRIPE_PRICE_ID_PREMIUM environment variable is not set")
    }

    private val baseUrl: String by lazy {
        System.getenv("APP_BASE_URL") ?: "https://app.writeopia.io"
    }

    fun initialize() {
        Stripe.apiKey = secretKey
        logger.info("Stripe SDK initialized")
    }

    fun createCustomer(email: String, userId: String): Customer {
        val params = CustomerCreateParams.builder()
            .setEmail(email)
            .putMetadata("userId", userId)
            .build()

        return Customer.create(params)
    }

    fun createCheckoutSession(
        customerId: String,
        userId: String,
        successUrl: String? = null,
        cancelUrl: String? = null
    ): Session {
        val effectiveSuccessUrl = successUrl
            ?: "$baseUrl/subscription/success?session_id={CHECKOUT_SESSION_ID}"
        val effectiveCancelUrl = cancelUrl
            ?: "$baseUrl/subscription/cancel"

        val params = SessionCreateParams.builder()
            .setCustomer(customerId)
            .setClientReferenceId(userId)
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceIdPremium)
                    .setQuantity(1)
                    .build()
            )
            .setSuccessUrl(effectiveSuccessUrl)
            .setCancelUrl(effectiveCancelUrl)
            .build()

        return Session.create(params)
    }

    fun createPortalSession(customerId: String, returnUrl: String? = null): PortalSession {
        val effectiveReturnUrl = returnUrl ?: "$baseUrl/subscription"

        val params = PortalSessionCreateParams.builder()
            .setCustomer(customerId)
            .setReturnUrl(effectiveReturnUrl)
            .build()

        return PortalSession.create(params)
    }

    fun constructWebhookEvent(payload: String, sigHeader: String): com.stripe.model.Event {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret)
    }

    fun getSubscription(subscriptionId: String): Subscription {
        return Subscription.retrieve(subscriptionId)
    }
}
