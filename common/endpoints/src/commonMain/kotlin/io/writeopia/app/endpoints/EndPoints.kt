package io.writeopia.app.endpoints

object EndPoints {
    fun localAiGenerate() = "generate"

    fun localAiModels() = "api/tags"

    fun introNotes() = "document/intro"

    fun userNotes() = "document/user/{id}"

    fun documents() = "document"

    fun documentsByParent() = "document/parent/{id}"

    fun proxyUserNotes(userId: String = "{userId}") = "proxy/document/user/$userId"

    fun userNotes(userId: String = "{userId}") = "document/user/$userId"

    // Vertex AI endpoints
    fun aiGenerate() = "api/ai/generate"

    fun aiSummary() = "api/ai/summary"

    fun aiActionPoints() = "api/ai/action-points"

    fun aiFaq() = "api/ai/faq"

    fun aiTags() = "api/ai/tags"

    fun aiStatus() = "api/ai/status"
}
