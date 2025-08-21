package io.writeopia.sqldelight.dao

import io.writeopia.app.sql.StoryStepEntityFtsQueries
import io.writeopia.app.sql.TokenEntityQueries
import io.writeopia.sdk.sql.StoryStepEntityQueries

class StoryStepFtsSqlDelightDao(private val storyStepEntityQueries: StoryStepEntityFtsQueries?) {

    fun searchInDocument(query: String, documentId: String): List<Int> =
        storyStepEntityQueries?.searchFts(query)
            ?.executeAsList()
            ?.filter { (_, documentIdFts) -> documentIdFts == documentId }
            ?.mapNotNull { (position, _) -> position?.toInt() }
            ?: emptyList()
}
