package io.writeopia.api.typesafe.service

import io.writeopia.api.typesafe.model.SystemOneQuestion
import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionScope
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The questions the writing agent asks Jev. Each action is one Noul (a yes/no judgment), because
 * several actions can fit the same paragraph at once: a list and a heading, for example. A Choice
 * would split one probability among them and nothing would reach the threshold.
 */
object WritingSuggestionQuestions {

    /** The document text is cut here to keep the request small. The paragraph is never cut. */
    const val DOCUMENT_TEXT_LIMIT = 6_000

    fun stateFor(request: WritingSuggestionsRequest): JsonObject =
        JsonObject(
            buildMap {
                when (request.scope) {
                    WritingSuggestionScope.WRITING -> {
                        put("paragraph", JsonPrimitive(request.text))
                        put("paragraph_type", JsonPrimitive(request.blockType ?: "message"))
                    }

                    WritingSuggestionScope.DOCUMENT_OPENED -> {}
                }
                put("document_title", JsonPrimitive(request.documentTitle ?: ""))
                put("document", JsonPrimitive((request.documentText ?: "").take(DOCUMENT_TEXT_LIMIT)))
            }
        )

    fun questionsFor(scope: WritingSuggestionScope): Map<String, SystemOneQuestion> =
        WritingSuggestionAction.forScope(scope).associate { action ->
            action.name to questionFor(action, scope)
        }

    /**
     * The question for [action]. The TL;DR and the conclusion are asked differently while writing
     * (does this paragraph call for one?) and when a document opens (does the document need one?).
     */
    fun questionFor(
        action: WritingSuggestionAction,
        scope: WritingSuggestionScope = WritingSuggestionScope.WRITING,
    ): SystemOneQuestion =
        when (action) {
            WritingSuggestionAction.CODE_BLOCK -> SystemOneQuestion.noul(
                "The writer of `paragraph` is about to show source code, a terminal command, a " +
                    "configuration file or another snippet, so a code block right after the " +
                    "paragraph would help.",
                whenTrue = "The paragraph introduces, explains or refers to code, a command or a " +
                    "snippet that should follow it, like 'run the following command' or 'the " +
                    "function looks like this'.",
                whenFalse = "The paragraph is prose with no code, command or snippet coming next.",
            )

            WritingSuggestionAction.LIST -> SystemOneQuestion.noul(
                "`paragraph` introduces or enumerates several items, options or points that " +
                    "would read better as a bullet list right after it.",
                whenTrue = "The paragraph announces a list ('there are three reasons', 'the " +
                    "ingredients are') or crams several parallel items into one sentence.",
                whenFalse = "The paragraph is a single idea, a narrative, or already a list item.",
            )

            WritingSuggestionAction.CHECK_LIST -> SystemOneQuestion.noul(
                "`paragraph` describes tasks, to-dos, or steps someone has to complete, which " +
                    "would be better tracked as a checklist with checkboxes right after it.",
                whenTrue = "The paragraph lists things to do, buy, verify or finish, or steps of a " +
                    "procedure to follow.",
                whenFalse = "The paragraph describes facts, opinions or events with nothing to " +
                    "complete or tick off.",
            )

            WritingSuggestionAction.NEW_DOCUMENT_LINK -> SystemOneQuestion.noul(
                "`paragraph` mentions a topic big enough to deserve a separate document of its " +
                    "own, linked from here, so the writer would benefit from creating that " +
                    "linked document now.",
                whenTrue = "The paragraph defers a topic ('more details in another page', 'the " +
                    "architecture is described separately') or names a subject that clearly " +
                    "needs its own page, like a project, a meeting series or a specification.",
                whenFalse = "The paragraph is self-contained and nothing in it calls for a " +
                    "separate page.",
            )

            WritingSuggestionAction.IMAGE -> SystemOneQuestion.noul(
                "`paragraph` refers to a picture, screenshot, photo, chart or other visual that " +
                    "should be inserted right after it.",
                whenTrue = "The paragraph says things like 'as shown in the screenshot', 'see " +
                    "the picture below' or describes something that is clearly meant to be " +
                    "shown as an image.",
                whenFalse = "The paragraph does not call for an image.",
            )

            WritingSuggestionAction.DRAWING -> SystemOneQuestion.noul(
                "`paragraph` describes a layout, flow, shape, or relationship that would be " +
                    "clearer with a quick hand-drawn sketch or diagram.",
                whenTrue = "The paragraph describes an architecture, a flow between parts, a " +
                    "map, a floor plan, a geometry, or explicitly mentions a diagram or sketch.",
                whenFalse = "The paragraph needs no sketch to be understood.",
            )

            WritingSuggestionAction.SPREADSHEET -> SystemOneQuestion.noul(
                "`paragraph` contains data with several attributes per item, comparisons, " +
                    "prices, dates or figures that would be better presented as a table.",
                whenTrue = "The paragraph compares options across criteria, lists items with " +
                    "numbers or properties, or reads like rows and columns written out in prose.",
                whenFalse = "The paragraph has no tabular data.",
            )

            WritingSuggestionAction.SECTION_HEADING -> SystemOneQuestion.noul(
                "`paragraph` opens a new section of `document` and would read better with a " +
                    "section heading (a Markdown `#`, `##` or `###` line) placed right above it. " +
                    "This is about a heading inside the document, not about the title of the " +
                    "whole document, which is `document_title`.",
                whenTrue = "The paragraph opens a topic distinct from the text before it in " +
                    "`document`, the document is long enough to have sections, and no section " +
                    "heading precedes the paragraph.",
                whenFalse = "The paragraph continues the current section, the document is a " +
                    "short note, or the paragraph is itself a heading.",
            )

            WritingSuggestionAction.DOCUMENT_TITLE -> SystemOneQuestion.noul(
                // Never sent: the app decides this one from the document itself. It is here so
                // the mapping stays exhaustive.
                "`document` has no title and would benefit from one.",
            )

            WritingSuggestionAction.CALLOUT -> SystemOneQuestion.noul(
                "`paragraph` is a warning, tip, important note, key takeaway, or a quotation of " +
                    "someone's words, so it should stand out from the text as a highlighted " +
                    "callout or quote block.",
                whenTrue = "The paragraph starts with or reads like 'Note:', 'Warning:', 'Tip:', " +
                    "'Important:', 'Remember', or it quotes someone else.",
                whenFalse = "The paragraph is regular body text.",
            )

            WritingSuggestionAction.TLDR -> when (scope) {
                WritingSuggestionScope.WRITING -> SystemOneQuestion.noul(
                    "`paragraph` announces or asks for a summary, overview or TL;DR of " +
                        "`document`, so writing a TL;DR at the top of the document would help.",
                    whenTrue = "The paragraph says things like 'in summary', 'to sum up', 'here is " +
                        "the short version', 'TL;DR', 'let's recap', or asks for the document to " +
                        "be summarized.",
                    whenFalse = "The paragraph is regular content that does not call for a " +
                        "summary of the document.",
                )

                WritingSuggestionScope.DOCUMENT_OPENED -> SystemOneQuestion.noul(
                    "`document` is long or dense enough that readers would benefit from a short " +
                        "TL;DR summary at the top, and it does not have one yet.",
                    whenTrue = "The document has several paragraphs or sections, covers more than " +
                        "one idea, and has no summary, abstract or TL;DR at the top.",
                    whenFalse = "The document is short, empty, a simple list, or already starts " +
                        "with a summary.",
                )
            }

            WritingSuggestionAction.CONCLUSION -> when (scope) {
                WritingSuggestionScope.WRITING -> SystemOneQuestion.noul(
                    "`paragraph` announces that `document` is about to conclude, or asks for a " +
                        "conclusion or wrap-up, so writing the conclusion of the document would " +
                        "help.",
                    whenTrue = "The paragraph says things like 'in conclusion', 'to conclude', " +
                        "'next, we conclude', 'finally', 'to wrap up', 'closing thoughts', or " +
                        "asks for a conclusion to be written.",
                    whenFalse = "The paragraph is regular content that does not announce or ask " +
                        "for the end of the document.",
                )

                WritingSuggestionScope.DOCUMENT_OPENED -> SystemOneQuestion.noul(
                    "`document` develops an argument, analysis or report that currently ends " +
                        "abruptly, so a conclusion or wrap-up at the end would help.",
                    whenTrue = "The document is an essay, report, proposal, analysis or article " +
                        "whose last paragraph does not close the discussion.",
                    whenFalse = "The document is a short note, a list, a log, or it already ends " +
                        "with a conclusion, summary or next steps.",
                )
            }
        }
}
