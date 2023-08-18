package com.github.leandroborgesferreira.storyteller.intronotes

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.github.leandroborgesferreira.storyteller.intronotes.data.supermarketList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReadIntroNotes : RequestHandler<Unit, APIGatewayProxyResponseEvent> {
    override fun handleRequest(input: Unit, context: Context): APIGatewayProxyResponseEvent {
        val response = handleRequest()

        return APIGatewayProxyResponseEvent()
            .withStatusCode(200)
            .withIsBase64Encoded(false)
            .withBody(response)
    }
}

fun handleRequest(): String = Json.encodeToString(supermarketList())