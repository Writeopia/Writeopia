package com.github.leandroborgesferreira.storyteller.persistence.tracker

import com.github.leandroborgesferreira.storyteller.manager.DocumentTracker
import com.github.leandroborgesferreira.storyteller.manager.DocumentUpdate
import com.github.leandroborgesferreira.storyteller.model.document.Document
import com.github.leandroborgesferreira.storyteller.model.document.DocumentInfo
import com.github.leandroborgesferreira.storyteller.model.story.LastEdit
import com.github.leandroborgesferreira.storyteller.model.story.StoryState
import com.github.leandroborgesferreira.storyteller.model.story.StoryTypes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import java.util.UUID

class OnUpdateDocumentTracker(private val documentUpdate: DocumentUpdate) : DocumentTracker {

    override suspend fun saveOnStoryChanges(
        documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>
    ) {
        documentEditionFlow.collectLatest { (storyState, documentInfo) ->
            when (val lastEdit = storyState.lastEdit) {
                is LastEdit.LineEdition -> {
                    documentUpdate.saveStoryStep(
                        storyStep = lastEdit.storyStep.copy(
                            localId = UUID.randomUUID().toString()
                        ),
                        position = lastEdit.position,
                        documentId = documentInfo.id
                    )
                }

                LastEdit.Nothing -> {}

                LastEdit.Whole -> {
                    val stories = storyState.stories
                    val titleFromContent = stories.values.firstOrNull { storyStep ->
                        //Todo: Change the type of change to allow different types. The client code should decide what is a title
                        //It is also interesting to inv
                        storyStep.type == StoryTypes.TITLE.type
                    }?.text

                    val document = Document(
                        id = documentInfo.id,
                        title = titleFromContent ?: documentInfo.title,
                        content = storyState.stories,
                        createdAt = documentInfo.createdAt,
                        lastUpdatedAt = documentInfo.lastUpdatedAt,
                    )

                    documentUpdate.saveDocument(document)
                }

                is LastEdit.InfoEdition -> {
                    val stories = storyState.stories
                    val titleFromContent = stories.values.firstOrNull { storyStep ->
                        //Todo: Change the type of change to allow different types. The client code should decide what is a title
                        //It is also interesting to inv
                        storyStep.type == StoryTypes.TITLE.type
                    }?.text

                    documentUpdate.saveDocumentMetadata(
                        Document(
                            id = documentInfo.id,
                            title = titleFromContent ?: documentInfo.title,
                            createdAt = documentInfo.createdAt,
                            lastUpdatedAt = documentInfo.lastUpdatedAt,
                        )
                    )

                    documentUpdate.saveStoryStep(
                        storyStep = lastEdit.storyStep,
                        position = lastEdit.position,
                        documentId = documentInfo.id
                    )
                }
            }
        }
    }
}
