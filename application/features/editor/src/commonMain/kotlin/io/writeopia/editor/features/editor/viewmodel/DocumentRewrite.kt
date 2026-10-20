package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.manager.InTextMarkdownHandler
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo

/**
 * A rewrite of the document by the AI, when a command is typed with nothing selected. The
 * region from [from] up to [toExclusive] goes to the AI as [markdown], one paragraph per line,
 * and comes back whole, rewritten; [parse] turns the answer into stories again.
 *
 * Blocks the AI cannot write, like spreadsheets, images and drawings, are not sent: a
 * `[[writeopia-block-N]]` line stands for each, and the block itself comes back untouched where
 * the answer leaves the line. The title is never part of the region. A long document is cut to
 * the paragraphs around the cursor that fit [MARKDOWN_LIMIT]; the rest stays as it is.
 */
class DocumentRewrite private constructor(
    val from: Double,
    val toExclusive: Double?,
    val markdown: String,
    /** The stories of the region before the rewrite, in order. */
    val originalStories: List<StoryStep>,
    private val keptBlocks: Map<Int, StoryStep>,
) {

    /** The blocks the AI does not see, in their order in the document. */
    val keptBlocksInOrder: List<StoryStep>
        get() = keptBlocks.entries.sortedBy { (number, _) -> number }.map { (_, block) -> block }

    /** The text of the region, for the skeleton that stands in for it. */
    val originalText: String
        get() = originalStories.mapNotNull { story -> story.text }.filter { it.isNotBlank() }.joinToString("\n")

    /** The answer of the AI as stories: [stories] in order, and the kept blocks the answer left out. */
    data class Parsed(val stories: List<StoryStep>, val pendingBlocks: List<StoryStep>)

    /** Reads [answer], possibly still incomplete, into stories. The kept blocks keep their ids. */
    fun parse(answer: String): Parsed {
        val lines = answer.lines().dropLastWhile { line -> line.isBlank() }
        val stories = mutableListOf<StoryStep>()
        val used = mutableSetOf<Int>()
        var codeLines: MutableList<String>? = null

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                val code = codeLines
                if (code == null) {
                    codeLines = mutableListOf()
                } else {
                    stories += StoryStep(type = StoryTypes.CODE_BLOCK.type, text = code.joinToString("\n"))
                    codeLines = null
                }
                continue
            }

            codeLines?.let { code ->
                code += line
                continue
            }

            val placeholder = PLACEHOLDER.matchEntire(trimmed)?.groupValues?.get(1)?.toIntOrNull()
            if (placeholder != null) {
                keptBlocks[placeholder]?.let { block ->
                    used += placeholder
                    stories += block
                }
                continue
            }

            stories += parseLine(trimmed)
        }

        // A fence still open while the answer streams shows its code as it comes.
        codeLines?.let { code ->
            stories += StoryStep(type = StoryTypes.CODE_BLOCK.type, text = code.joinToString("\n"))
        }

        val pending = keptBlocks.entries
            .filter { (number, _) -> number !in used }
            .sortedBy { (number, _) -> number }
            .map { (_, block) -> block }

        return Parsed(stories, pending)
    }

    /**
     * The region as it was, to put back when the AI fails. The rewritten stories get new ids,
     * since theirs were deleted when the rewrite began; the kept blocks keep theirs.
     */
    fun restoredStories(): List<StoryStep> {
        val keptIds = keptBlocks.values.map { block -> block.id }.toSet()

        return originalStories.map { story ->
            if (story.id in keptIds) story else story.copy(id = GenerateId.generate(), localId = GenerateId.generate())
        }
    }

    private fun parseLine(trimmed: String): StoryStep {
        val check = CHECK_ITEM.matchEntire(trimmed)

        val step = when {
            trimmed.isEmpty() -> StoryStep(type = StoryTypes.TEXT.type, text = "")
            DIVIDER.matches(trimmed) -> StoryStep(type = StoryTypes.DIVIDER.type)
            trimmed.startsWith("#### ") -> heading(trimmed.drop(5), Tag.H4)
            trimmed.startsWith("### ") -> heading(trimmed.drop(4), Tag.H3)
            trimmed.startsWith("## ") -> heading(trimmed.drop(3), Tag.H2)
            trimmed.startsWith("# ") -> heading(trimmed.drop(2), Tag.H1)
            check != null -> StoryStep(
                type = StoryTypes.CHECK_ITEM.type,
                text = check.groupValues[2].trim(),
                checked = check.groupValues[1].equals("x", ignoreCase = true)
            )
            trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                StoryStep(type = StoryTypes.UNORDERED_LIST_ITEM.type, text = trimmed.drop(2).trim())
            else -> StoryStep(type = StoryTypes.TEXT.type, text = trimmed)
        }

        return if (step.text.isNullOrEmpty()) step else InTextMarkdownHandler.handleMarkdown(step)
    }

    private fun heading(text: String, tag: Tag): StoryStep =
        StoryStep(type = StoryTypes.TEXT.type, text = text.trim(), tags = setOf(TagInfo(tag)))

    companion object {
        /** The Markdown sent to the AI is at most this long. */
        const val MARKDOWN_LIMIT = 20_000

        private val PLACEHOLDER = Regex("""\[\[writeopia-block-(\d+)]]""")
        private val CHECK_ITEM = Regex("""^(?:[-*]\s*)?\[([ xX]?)]\s?(.*)$""")
        private val DIVIDER = Regex("""^-{3,}$""")

        /** The types the AI rewrites. Everything else is a kept block, which it does not see. */
        private val rewritable = setOf(
            StoryTypes.TEXT.type,
            StoryTypes.CHECK_ITEM.type,
            StoryTypes.UNORDERED_LIST_ITEM.type,
            StoryTypes.CODE_BLOCK.type,
            StoryTypes.AI_ANSWER.type,
            StoryTypes.DIVIDER.type,
        )

        fun placeholder(number: Int): String = "[[writeopia-block-$number]]"

        /**
         * The rewrite of [stories] around [cursor], or null when there is nothing to rewrite.
         * The whole document (but the title) when its Markdown fits [limit], else the paragraphs
         * around the cursor that do.
         */
        fun build(stories: Map<Double, StoryStep>, cursor: Double?, limit: Int = MARKDOWN_LIMIT): DocumentRewrite? {
            val entries = stories.entries
                .filter { (_, step) -> step.type != StoryTypes.TITLE.type && !step.ephemeral }
                .sortedBy { (position, _) -> position }
            if (entries.isEmpty()) return null

            val lines = entries.map { (_, step) -> markdownOf(step) }
            val sizes = lines.map { stepLines -> stepLines.sumOf { line -> line.length + 1 } }

            var low = 0
            var high = entries.lastIndex

            if (sizes.sum() > limit) {
                val center = entries.indexOfFirst { (position, _) -> cursor != null && position >= cursor }
                    .takeIf { index -> index >= 0 } ?: entries.lastIndex
                low = center
                high = center
                var size = sizes[center]
                var growBefore = true

                while (true) {
                    val before = low - 1
                    val after = high + 1
                    val canBefore = before >= 0 && size + sizes[before] <= limit
                    val canAfter = after <= entries.lastIndex && size + sizes[after] <= limit

                    when {
                        growBefore && canBefore -> {
                            low = before
                            size += sizes[before]
                        }

                        canAfter -> {
                            high = after
                            size += sizes[after]
                        }

                        canBefore -> {
                            low = before
                            size += sizes[before]
                        }

                        else -> break
                    }
                    growBefore = !growBefore
                }
            }

            val region = entries.subList(low, high + 1)
            val kept = mutableMapOf<Int, StoryStep>()
            val markdown = buildList {
                region.forEachIndexed { index, (_, step) ->
                    if (step.type in rewritable) {
                        addAll(lines[low + index])
                    } else {
                        val number = kept.size + 1
                        kept[number] = step
                        add(placeholder(number))
                    }
                }
            }.joinToString("\n")

            return DocumentRewrite(
                from = region.first().key,
                toExclusive = entries.getOrNull(high + 1)?.key,
                markdown = markdown,
                originalStories = region.map { (_, step) -> step },
                keptBlocks = kept,
            )
        }

        private fun markdownOf(step: StoryStep): List<String> {
            val text = step.text ?: ""

            return when {
                step.type !in rewritable -> listOf(placeholder(0))
                step.type == StoryTypes.DIVIDER.type -> listOf("---")
                step.type == StoryTypes.CODE_BLOCK.type || step.tags.any { it.tag == Tag.CODE_BLOCK } ->
                    listOf("```") + text.lines() + listOf("```")
                step.type == StoryTypes.CHECK_ITEM.type -> listOf("- [${if (step.checked == true) "x" else " "}] $text")
                step.type == StoryTypes.UNORDERED_LIST_ITEM.type -> listOf("- $text")
                step.tags.any { it.tag == Tag.H1 } -> listOf("# $text")
                step.tags.any { it.tag == Tag.H2 } -> listOf("## $text")
                step.tags.any { it.tag == Tag.H3 } -> listOf("### $text")
                step.tags.any { it.tag == Tag.H4 } -> listOf("#### $text")
                else -> listOf(text.replace("\n", " "))
            }
        }
    }
}
