package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class DeleteAccountResponse(val deleted: Boolean)
