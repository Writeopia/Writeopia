import Foundation
import WrModels

/// Adds, removes, splits and merges steps. Mirrors `ContentHandler` of the Kotlin SDK.
///
/// Positions are sort keys (see `Positions.swift`): a structural change never renumbers the
/// document, a new step goes to a rank between its neighbours, which always exists. Each change
/// returns where the focus and cursor should go; the `.whole` edit saves every step whose
/// position changed.
public struct ContentManager {
    private let focusHandler: FocusHandler
    private let lineBreakType: (StoryType) -> StoryType

    public init(
        focusHandler: FocusHandler = FocusHandler(),
        lineBreakType: @escaping (StoryType) -> StoryType = ContentManager.defaultLineBreakType
    ) {
        self.focusHandler = focusHandler
        self.lineBreakType = lineBreakType
    }

    /// A line break in the title starts a regular paragraph; lists and checklists continue.
    public static func defaultLineBreakType(_ type: StoryType) -> StoryType {
        switch type.number {
        case StoryType.title.number, StoryType.aiAnswer.number: .text
        default: type
        }
    }

    /// Tags that continue on the next line after a line break, like `mustCarryOver` in the SDK.
    private static let carryOverTags: Set<String> = ["HIGH_LIGHT_BLOCK", "CARD_BLOCK"]

    // MARK: - Line edition

    public func changeStoryState(_ change: Action.StoryStateChange, in stories: [String: StoryStep]) -> StoryState? {
        guard stories[change.position] != nil else { return nil }

        var newStories = stories
        newStories.place(change.storyStep, at: change.position)
        let cursor = change.selectionEnd ?? change.storyStep.text?.utf16.count ?? 0

        return StoryState(
            stories: newStories,
            lastEdit: .lineEdition(position: change.position, storyStep: newStories[change.position]!),
            focus: change.position,
            selection: Selection(position: change.position, start: change.selectionStart ?? cursor, end: cursor)
        )
    }

    public func changeStoryType(
        at position: String,
        to type: StoryType,
        in stories: [String: StoryStep],
        cursor: Int = 0
    ) -> StoryState? {
        guard var step = stories[position] else { return nil }

        step.type = type
        if type.number != StoryType.checkItem.number {
            step.checked = nil
        } else if step.checked == nil {
            step.checked = false
        }

        var newStories = stories
        newStories[position] = step
        return StoryState(
            stories: newStories,
            lastEdit: .lineEdition(position: position, storyStep: step),
            focus: position,
            selection: .cursor(cursor, at: position)
        )
    }

    // MARK: - Structure

    /// Splits the step at every line break of its text. The first line keeps the position of
    /// the step, the others go between it and the next step. The focus goes to the line that
    /// holds the cursor.
    public func onLineBreak(_ lineBreak: Action.LineBreak, in stories: [String: StoryStep]) -> StoryState {
        guard stories[lineBreak.position] != nil else { return StoryState(stories: stories) }

        let step = lineBreak.storyStep
        let text = step.text ?? ""

        // Like the Compose app, Return on an empty list or check item adds another item; the
        // list is left with Backspace at the start of the empty item.
        let lines = SpansHandler.splitLines(text, spans: step.spans)
        let carriedTags = step.tags.filter { Self.carryOverTags.contains($0.tag) }
        let newType = lineBreakType(step.type)

        var first = step
        first.text = lines[0].text
        first.spans = lines[0].spans

        let newSteps = lines.dropFirst().map { line in
            StoryStep(
                type: newType,
                text: line.text,
                checked: newType.number == StoryType.checkItem.number ? false : nil,
                tags: carriedTags,
                spans: line.spans,
                position: Rank.first
            )
        }

        var newStories = stories
        newStories.place(first, at: lineBreak.position)
        let lineKeys = [lineBreak.position] + newStories.insert(newSteps, after: lineBreak.position).keys

        // Which line has the cursor, and where inside it.
        var remaining = lineBreak.cursor
        var focusLine = lines.count - 1
        for (lineIndex, line) in lines.enumerated() {
            let length = line.text.utf16.count
            if remaining <= length {
                focusLine = lineIndex
                break
            }
            remaining -= length + 1
        }
        let cursorInLine = min(max(remaining, 0), lines[focusLine].text.utf16.count)

        let focus = lineKeys[focusLine]
        return StoryState(
            stories: newStories,
            lastEdit: .whole,
            focus: focus,
            selection: .cursor(cursorInLine, at: focus)
        )
    }

    /// Tags that Backspace at the start removes before merging the line, like the Compose app.
    static let erasableTags: Set<String> = [
        BlockTag.h1.rawValue, BlockTag.h2.rawValue, BlockTag.h3.rawValue, BlockTag.h4.rawValue,
        BlockTag.box.rawValue, BlockTag.card.rawValue,
    ]

    /// Backspace at the start of a step.
    ///
    /// - A list or checklist item becomes a regular paragraph first.
    /// - A heading, box or card becomes a plain paragraph first.
    /// - When the previous step isn't text (divider, link...), that step is removed.
    /// - Otherwise the text joins the previous text step, keeping its spans.
    public func onErase(_ erase: Action.EraseStory, in stories: [String: StoryStep]) -> StoryState? {
        let step = erase.storyStep

        if step.isListLike {
            var newStep = step
            newStep.type = .text
            newStep.checked = nil
            var newStories = stories
            newStories.place(newStep, at: erase.position)
            return StoryState(stories: newStories, lastEdit: .whole, focus: erase.position)
        }

        if !step.isTitle, step.tags.contains(where: { Self.erasableTags.contains($0.tag) }) {
            var newStep = step
            newStep.tags.removeAll { Self.erasableTags.contains($0.tag) }
            var newStories = stories
            newStories.place(newStep, at: erase.position)
            return StoryState(stories: newStories, lastEdit: .whole, focus: erase.position)
        }

        guard !step.isTitle,
              stories[erase.position] != nil,
              let previousKey = stories.previousKey(before: erase.position),
              let previous = stories[previousKey]
        else { return nil }

        var newStories = stories

        if !previous.isTextStep {
            newStories.removeValue(forKey: previousKey)
            return StoryState(
                stories: newStories,
                lastEdit: .whole,
                focus: erase.position,
                selection: .cursor(0, at: erase.position)
            )
        }

        let previousText = previous.text ?? ""
        let previousLength = previousText.utf16.count
        var merged = previous
        merged.text = previousText + (step.text ?? "")
        merged.spans = previous.spans + SpansHandler.shift(step.spans, by: previousLength)

        newStories[previousKey] = merged
        newStories.removeValue(forKey: erase.position)

        return StoryState(
            stories: newStories,
            lastEdit: .whole,
            focus: previousKey,
            selection: .cursor(previousLength, at: previousKey)
        )
    }

    public func onDelete(_ delete: Action.DeleteStory, in stories: [String: StoryStep]) -> StoryState {
        var newStories = stories
        newStories.removeValue(forKey: delete.position)
        let focus = focusHandler.findPreviousFocus(before: delete.position, in: newStories)
        let cursor = focus.flatMap { newStories[$0]?.text?.utf16.count } ?? 0

        return StoryState(
            stories: newStories,
            lastEdit: .whole,
            focus: focus,
            selection: focus.map { .cursor(cursor, at: $0) }
        )
    }

    /// Moves a step to right after another one: between the target and the step that follows
    /// it, so nothing else moves. The title always stays first.
    public func move(_ move: Action.Move, in stories: [String: StoryStep]) -> StoryState? {
        guard move.positionFrom != move.positionTo,
              let step = stories[move.positionFrom],
              !step.isTitle,
              stories[move.positionTo] != nil
        else { return nil }

        var newStories = stories
        newStories.removeValue(forKey: move.positionFrom)
        let newKey = newStories.insert(step, after: move.positionTo).key

        return StoryState(stories: newStories, lastEdit: .whole, focus: newKey)
    }

    /// Adds `step` right after `position`, or at the end when `position` is nil.
    public func add(_ step: StoryStep, after position: String?, in stories: [String: StoryStep]) -> StoryState {
        var newStories = stories
        let key: String
        if let position {
            key = newStories.insert(step, after: position).key
        } else {
            key = newStories.append([step])[0]
        }
        return StoryState(
            stories: newStories,
            lastEdit: .whole,
            focus: step.isTextStep ? key : nil,
            selection: .cursor(0, at: key)
        )
    }

    /// Adds `step` right before the step at `position`, or at `position` when it is free.
    public func add(_ step: StoryStep, before position: String, in stories: [String: StoryStep]) -> StoryState {
        var newStories = stories
        let key = newStories.insert(step, before: position).key
        return StoryState(
            stories: newStories,
            lastEdit: .whole,
            focus: step.isTextStep ? key : nil,
            selection: .cursor(0, at: key)
        )
    }

    // MARK: - Helpers

    /// Turns the text step at `position` into a link card for `url`, like `createLinkCard` of
    /// the Kotlin SDK. A card can not be typed in, so the cursor goes to the text line below
    /// it, which is added when there is none. The description of the page stays empty: the app
    /// asks the backend for it.
    public func createLinkCard(url: String, at position: String, in stories: [String: StoryStep]) -> StoryState {
        guard var card = stories[position] else { return StoryState(stories: stories) }

        card.type = .linkCard
        card.url = url
        card.text = nil
        card.tags = []
        card.spans = []

        var newStories = stories
        newStories[position] = card

        let next = newStories.nextKey(after: position)
        let below: String
        if let next, newStories[next]?.type.number == StoryType.text.number {
            below = next
        } else {
            below = newStories.insert(StoryStep(type: .text, text: "", position: Rank.first), after: position).key
        }

        return StoryState(
            stories: newStories,
            lastEdit: .whole,
            focus: below,
            selection: .cursor(0, at: below)
        )
    }

    /// Positions one integer apart, from `Rank.first`, for the steps of a new document, stored in
    /// each step as well. Only for documents that don't exist yet: the steps of an existing document keep
    /// their positions, see `WriteopiaManager.loadDocument`.
    public func positioned(_ steps: [StoryStep]) -> [String: StoryStep] {
        var result: [String: StoryStep] = [:]
        result.append(steps)
        return result
    }
}
