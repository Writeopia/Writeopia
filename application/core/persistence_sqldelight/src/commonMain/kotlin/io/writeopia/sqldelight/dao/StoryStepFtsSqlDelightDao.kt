package io.writeopia.sqldelight.dao

import io.writeopia.app.sql.StoryStepEntityFtsQueries
import io.writeopia.app.sql.TokenEntityQueries
import io.writeopia.sdk.sql.StoryStepEntityQueries
import io.writeopia.sql.WriteopiaDb

class StoryStepFtsSqlDelightDao(writeopiaDb: WriteopiaDb?) {

    private val storyStepEntityQueries: StoryStepEntityFtsQueries? =
        writeopiaDb?.storyStepEntityFtsQueries

    fun searchInDocument(query: String, documentId: String): List<Int> =
        storyStepEntityQueries?.searchFts(query)
            ?.executeAsList()
            ?.filter { (_, documentIdFts) -> documentIdFts == documentId }
            ?.mapNotNull { (position, _) -> position?.toInt() }
            ?: emptyList()
}
