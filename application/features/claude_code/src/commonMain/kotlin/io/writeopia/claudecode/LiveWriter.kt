package io.writeopia.claudecode

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Types blocks into an open editor, a few characters at a time, so the person sees the text
 * being written. Like the AI answers of the editor: the block is ephemeral while it is typed
 * (never saved nor synced), then saved once whole, through the editor's own trackers.
 *
 * Only adds: blocks go after the last one, and the trailing empty paragraph of the document, if
 * any, is filled instead of left behind (keeping its id). No step is ever removed, so no
 * tombstone is needed.
 */
class LiveWriter(
    private val tick: Duration = 16.milliseconds,
    /** The longest a call types for: a long text is typed faster, never slower than [minCharsPerTick]. */
    private val maxDuration: Duration = 8.seconds,
    private val minCharsPerTick: Int = 3,
) {

    /**
     * Writes [title] into the title block (when given), then [blocks] at the end. Checks
     * [isStopped] at every step: once true, the block being typed is saved as far as it got and
     * nothing more is written.
     *
     * @return true when everything was written, false when it was stopped.
     */
    suspend fun write(
        manager: WriteopiaStateManager,
        title: String?,
        blocks: List<StoryStep>,
        isStopped: () -> Boolean = { false },
    ): Boolean {
        val total = (title?.length ?: 0) + blocks.sumOf { block -> if (block.isTyped()) block.text?.length ?: 0 else 0 }
        val ticks = max(1, (maxDuration / tick).toInt())
        val perTick = max(minCharsPerTick, ceil(total.toDouble() / ticks).toInt())

        if (title != null) {
            val titleEntry = manager.currentStory.value.stories.entries
                .firstOrNull { (_, step) -> step.type == StoryTypes.TITLE.type }
            if (titleEntry != null) {
                val typed = type(manager, titleEntry.key, titleEntry.value.copy(text = title, spans = emptySet()), perTick, isStopped)
                if (!typed) return stopped(manager)
            }
        }

        var reusable = trailingEmptyParagraph(manager)

        for (block in blocks) {
            if (isStopped()) return stopped(manager)

            val reused = reusable?.let { position -> manager.getStory(position)?.let { position to it } }
            reusable = null

            val (position, step) = if (reused != null) {
                val (position, existing) = reused
                position to block.copy(id = existing.id)
            } else {
                val start = if (block.isTyped()) block.partial(0) else block
                manager.addAtPosition(start, Rank.after(manager.lastPosition())) to block
            }

            // Follow the writing down the document.
            manager.currentStory.value.stories.keys.indexOf(position)
                .takeIf { it >= 0 }
                ?.let(manager::scrollToPosition)

            if (step.isTyped()) {
                if (!type(manager, position, step, perTick, isStopped)) return stopped(manager)
            } else {
                manager.changeStoryState(
                    Action.StoryStateChange(step.copyNewLocalId(), position, preserveFocus = true),
                    trackIt = false
                )
            }
        }

        manager.trackState()
        return true
    }

    private fun stopped(manager: WriteopiaStateManager): Boolean {
        manager.trackState()
        return false
    }

    /** Types [step] at [position]; false when it was stopped, with the text typed so far saved. */
    private suspend fun type(
        manager: WriteopiaStateManager,
        position: String,
        step: StoryStep,
        perTick: Int,
        isStopped: () -> Boolean,
    ): Boolean {
        val text = step.text.orEmpty()
        var shown = 0

        while (shown < text.length) {
            if (isStopped()) {
                // What the person saw stays: the block as far as it got, saved.
                manager.changeStoryState(
                    Action.StoryStateChange(step.partial(shown).copy(ephemeral = false), position, preserveFocus = true),
                    trackIt = false
                )
                return false
            }

            shown = min(text.length, shown + perTick)
            // Never cut a character made of two UTF-16 units in half.
            if (shown < text.length && text[shown - 1].isHighSurrogate()) shown++

            manager.changeStoryState(
                Action.StoryStateChange(step.partial(shown), position, preserveFocus = true),
                trackIt = false
            )
            delay(tick)
        }

        manager.changeStoryState(
            Action.StoryStateChange(step.copy(ephemeral = false).copyNewLocalId(), position, preserveFocus = true),
            trackIt = false
        )
        return true
    }

    private fun trailingEmptyParagraph(manager: WriteopiaStateManager): String? {
        val position = manager.currentStory.value.stories.keys.lastOrNull() ?: return null
        val step = manager.getStory(position) ?: return null
        return position.takeIf {
            step.type == StoryTypes.TEXT.type && step.text.isNullOrBlank() && step.tags.isEmpty() && !step.ephemeral
        }
    }

    private fun StoryStep.isTyped(): Boolean = type in typedTypes && !text.isNullOrEmpty()

    private companion object {
        val typedTypes = setOf(
            StoryTypes.TITLE.type,
            StoryTypes.TEXT.type,
            StoryTypes.UNORDERED_LIST_ITEM.type,
            StoryTypes.CHECK_ITEM.type,
            StoryTypes.CODE_BLOCK.type,
        )
    }
}

/**
 * The step with its first [length] characters, the spans cut to them, ephemeral. It gets a new
 * local id: a text field of the editor keeps its own copy of the text and only reads the step
 * again when it is drawn anew, which a new local id asks for.
 */
internal fun StoryStep.partial(length: Int): StoryStep {
    val text = text.orEmpty()
    val shown = min(length, text.length)
    return copy(
        text = text.take(shown),
        spans = spans
            .filter { span -> span.start < shown }
            .map { span -> SpanInfo.create(span.start, min(span.end, shown), span.span, span.extra) }
            .toSet(),
        ephemeral = true,
        localId = GenerateId.generate(),
    )
}
