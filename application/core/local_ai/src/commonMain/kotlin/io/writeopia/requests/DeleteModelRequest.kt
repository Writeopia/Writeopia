package io.writeopia.requests

import kotlinx.serialization.Serializable

@Serializable
data class DeleteModelRequest(val model: String)
