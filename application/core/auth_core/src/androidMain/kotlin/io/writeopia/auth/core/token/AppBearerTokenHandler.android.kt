package io.writeopia.auth.core.token

import io.writeopia.sdk.network.oauth.BearerTokenHandler

actual object AppBearerTokenHandler : BearerTokenHandler {
    actual override suspend fun getIdToken(): String? = null
}
