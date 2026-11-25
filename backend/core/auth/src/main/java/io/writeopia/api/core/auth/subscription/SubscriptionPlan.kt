package io.writeopia.api.core.auth.subscription

/**
 * The price of the monthly subscription, shown by the paywall of the apps.
 *
 * Set by the environment of the auth service (`SUBSCRIPTION_MONTHLY_PRICE_CENTS`,
 * `SUBSCRIPTION_CURRENCY`), so it changes with a redeploy and no app release.
 *
 * @property monthlyPriceCents In the minor unit of [currency] (cents for USD).
 * @property currency ISO 4217 code.
 */
data class SubscriptionPlan(
    val monthlyPriceCents: Long,
    val currency: String,
) {
    init {
        require(monthlyPriceCents >= 0) { "The monthly price can't be negative" }
        require(currency.length == 3 && currency.all { it.isUpperCase() }) {
            "The currency must be an ISO 4217 code, such as USD"
        }
    }

    companion object {
        const val DEFAULT_MONTHLY_PRICE_CENTS = 500L
        const val DEFAULT_CURRENCY = "USD"

        fun fromEnv(env: (String) -> String? = System::getenv): SubscriptionPlan =
            SubscriptionPlan(
                monthlyPriceCents = env("SUBSCRIPTION_MONTHLY_PRICE_CENTS")?.trim()?.toLongOrNull()
                    ?: DEFAULT_MONTHLY_PRICE_CENTS,
                currency = env("SUBSCRIPTION_CURRENCY")?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
                    ?: DEFAULT_CURRENCY,
            )
    }
}
