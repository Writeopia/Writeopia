package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.writeopia.api.core.auth.subscription.SubscriptionPlan
import io.writeopia.api.core.auth.utils.getUserIdFromApiGateway
import io.writeopia.api.core.auth.utils.isPremiumUser
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.serialization.response.SubscriptionResponse
import io.writeopia.sql.WriteopiaDbBackend

/**
 * `GET /api/auth/subscription`: whether the signed-in user is premium, and the monthly price.
 * The apps ask it right after signing in or signing up and show the paywall to a user who is not.
 *
 * In debug mode every user is premium, as the premium checks of the other routes are skipped there.
 */
fun Routing.subscriptionRoute(
    writeopiaDb: WriteopiaDbBackend,
    debugMode: Boolean = false,
    plan: SubscriptionPlan = SubscriptionPlan.fromEnv(),
) {
    get("/${EndPoints.subscription()}") {
        val userId = call.getUserIdFromApiGateway(debugMode)
        if (userId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            return@get
        }

        val premium = debugMode || writeopiaDb.isPremiumUser(userId)

        // Changes the moment the user subscribes: never served from a cache.
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respond(
            HttpStatusCode.OK,
            SubscriptionResponse(
                premium = premium,
                tier = if (premium) Tier.PREMIUM.name else Tier.FREE.name,
                monthlyPriceCents = plan.monthlyPriceCents,
                currency = plan.currency,
            )
        )
    }
}
