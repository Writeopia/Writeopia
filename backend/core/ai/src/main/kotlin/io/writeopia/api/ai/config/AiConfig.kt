package io.writeopia.api.ai.config

object AiConfig {
    /**
     * Monthly token quota for premium users.
     * Can be configured via MONTHLY_TOKEN_QUOTA environment variable.
     * Defaults to 100,000 tokens if not set.
     */
    fun monthlyTokenQuota(): Long =
        System.getenv("MONTHLY_TOKEN_QUOTA")?.toLongOrNull() ?: 100_000L

    /**
     * Account type identifier for premium accounts.
     */
    const val ACCOUNT_TYPE_PREMIUM = "PREMIUM"
}
