package io.writeopia.sdk.import.markdown

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.io.File

actual object MarkdownToDocument {

    actual fun readDocuments(
        files: List<String>,
        userId: String,
        parentId: String
    ): Flow<Document> =
        files.asFlow()
            .map(::File)
            .filter { file -> file.extension == "md" }
            .map { file ->
                val content = MarkdownParser.parse(file.readLines())
                val title = content.firstOrNull { storyStepApi ->
                    storyStepApi.type.number == StoryTypes.TITLE.type.number
                }?.text

                DocumentApi(
                    id = GenerateId.generate(),
                    title = title ?: "",
                    workspaceId = userId,
                    parentId = parentId,
                    isLocked = false,
                    content = content,
                    isFavorite = false,
                    deleted = false
                )
            }
            .map { it.toModel() }

    actual fun readMarkdown(markdownText: String, userId: String, parentId: String): Document? {
        val lines = markdownText.split("\n")
        val story = MarkdownParser.parse(lines)

        val title = story.firstOrNull { it.type.number == StoryTypes.TITLE.type.number }?.text ?: ""

        return DocumentApi(
            id = GenerateId.generate(),
            title = title,
            workspaceId = userId,
            parentId = parentId,
            content = story,
            isFavorite = false,
            deleted = false,
            isLocked = false,
        ).toModel()
    }
}
