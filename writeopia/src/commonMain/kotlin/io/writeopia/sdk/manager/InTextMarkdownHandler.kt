package io.writeopia.sdk.manager

import io.writeopia.sdk.models.markdown.InlineMarkdownParser
import io.writeopia.sdk.models.story.StoryStep

/**
 * Handles markdown parsing for text within the Editor.
 * Delegates to [InlineMarkdownParser] for the actual parsing logic.
 */
object InTextMarkdownHandler {

    /**
     * Parses markdown syntax in a StoryStep's text and converts to spans.
     *
     * @param storyStep The StoryStep to parse
     * @return A new StoryStep with parsed spans, or the original if no markdown was found
     */
    fun handleMarkdown(storyStep: StoryStep): StoryStep =
        InlineMarkdownParser.parseMarkdown(storyStep)
}
