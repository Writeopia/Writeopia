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
     * The tokens charged for a generated image when the model reports no usage, so a picture
     * always counts in the quota. 1290 is what Gemini charges for one 1024x1024 image.
     * Can be configured via IMAGE_TOKEN_COST environment variable.
     */
    fun imageTokenCost(): Int =
        System.getenv("IMAGE_TOKEN_COST")?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_IMAGE_TOKEN_COST

    const val DEFAULT_IMAGE_TOKEN_COST = 1290

    /**
     * Account type identifier for premium accounts.
     */
    const val ACCOUNT_TYPE_PREMIUM = "PREMIUM"
}
