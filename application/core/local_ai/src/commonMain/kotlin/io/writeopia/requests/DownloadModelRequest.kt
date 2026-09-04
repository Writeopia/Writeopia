package io.writeopia.requests

import kotlinx.serialization.Serializable

@Serializable
data class DownloadModelRequest(val model: String)
