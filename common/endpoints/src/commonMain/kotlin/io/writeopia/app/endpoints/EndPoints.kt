package io.writeopia.app.endpoints

object EndPoints {
    fun ollamaGenerate() = "generate"

    fun ollamaModels() = "api/tags"

    fun introNotes() = "document/intro"

    fun userNotes() = "document/user/{id}"

    fun documents() = "document"

    fun documentsByParent() = "document/parent/{id}"

    fun proxyUserNotes(userId: String = "{userId}") = "proxy/document/user/$userId"

    fun userNotes(userId: String = "{userId}") = "document/user/$userId"
}
