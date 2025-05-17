package io.writeopia.sdk.models.user

data class WriteopiaUser(
    val id: String,
    val email: String,
    val password: String,
    val name: String
) {
    companion object {
        fun disconnectedUser(): WriteopiaUser = WriteopiaUser(id = "disconnected_user", "", "", "")
    }
}
