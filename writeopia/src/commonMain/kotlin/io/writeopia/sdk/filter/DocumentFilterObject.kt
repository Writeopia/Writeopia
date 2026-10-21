package io.writeopia.sdk.filter

import io.writeopia.sdk.utils.alias.DocumentContent

/**
 * Default implementation of [DocumentFilter]
 */
object DocumentFilterObject : DocumentFilter {

    /** The steps that are not of [removeTypes], at their positions: nothing is renumbered. */
    override fun removeTypesFromDocument(
        documentContent: DocumentContent,
        removeTypes: Set<Int>
    ): DocumentContent =
        documentContent.filterValues { storyStep ->
            !removeTypes.contains(storyStep.type.number)
        }
}
