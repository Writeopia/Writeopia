package io.writeopia.sdk.network.subscription

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.serialization.Serializable

@Serializable
data class CheckoutSessionRequest(
    val successUrl: String? = null,
    val cancelUrl: String? = null
)

@Serializable
data class CheckoutSessionResponse(
    val checkoutUrl: String
)

@Serializable
data class PortalSessionResponse(
    val portalUrl: String
)

@Serializable
data class SubscriptionResponse(
    val planId: String,
    val status: String,
    val currentPeriodStart: Long?,
    val currentPeriodEnd: Long?,
    val cancelAtPeriodEnd: Boolean
)

class SubscriptionApi(private val client: HttpClient, private val baseUrl: String) {

    suspend fun createCheckoutSession(
        token: String,
        successUrl: String? = null,
        cancelUrl: String? = null
    ): ResultData<CheckoutSessionResponse> {
        return try {
            val response = client.post("$baseUrl/${EndPoints.billingCheckoutSession()}") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(CheckoutSessionRequest(successUrl, cancelUrl))
            }
            ResultData.Complete(response.body())
        } catch (e: Exception) {
            ResultData.Error(e)
        }
    }

    suspend fun createPortalSession(token: String): ResultData<PortalSessionResponse> {
        return try {
            val response = client.post("$baseUrl/${EndPoints.billingPortalSession()}") {
                bearerAuth(token)
            }
            ResultData.Complete(response.body())
        } catch (e: Exception) {
            ResultData.Error(e)
        }
    }

    suspend fun getSubscription(token: String): ResultData<SubscriptionResponse> {
        return try {
            val response = client.get("$baseUrl/${EndPoints.billingSubscription()}") {
                bearerAuth(token)
            }
            ResultData.Complete(response.body())
        } catch (e: Exception) {
            ResultData.Error(e)
        }
    }
}
