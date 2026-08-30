package io.writeopia.sdk.models.user

data class WriteopiaUser(
    val id: String,
    val email: String,
    val name: String,
    val tier: Tier = Tier.FREE
) {
    companion object {
        const val DISCONNECTED = "disconnected_user"
        const val NO_USER = "no_user"

        fun disconnectedUser(): WriteopiaUser = WriteopiaUser(id = DISCONNECTED, "", "")

        fun noUser(): WriteopiaUser = WriteopiaUser(id = NO_USER, "", "")
    }
}

enum class Tier {
    FREE,
    PREMIUM;

    fun tierName() =
        when (this) {
            FREE -> "FREE"
            PREMIUM -> "PREMIUM"
        }
}
