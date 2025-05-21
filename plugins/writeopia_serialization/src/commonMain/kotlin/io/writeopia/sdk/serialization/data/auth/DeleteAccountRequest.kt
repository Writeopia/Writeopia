package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class DeleteAccountRequest(val id: String)
