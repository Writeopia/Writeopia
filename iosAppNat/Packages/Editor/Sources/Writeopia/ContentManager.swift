import Foundation
import WrModels

/// Adds, removes, splits and merges steps. Mirrors `ContentHandler` of the Kotlin SDK.
///
/// Every structural change renumbers the steps to 0, 1, 2... so positions stay simple, and
/// returns where the focus and cursor should go.
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

    public func changeStoryState(_ change: Action.StoryStateChange, in stories: [Double: StoryStep]) -> StoryState? {
        guard stories[change.position] != nil else { return nil }

        var newStories = stories
        newStories[change.position] = change.storyStep
        let cursor = change.selectionEnd ?? change.storyStep.text?.utf16.count ?? 0

        return StoryState(
            stories: newStories,
            lastEdit: .lineEdition(position: change.position, storyStep: change.storyStep),
            focus: change.position,
            selection: Selection(position: change.position, start: change.selectionStart ?? cursor, end: cursor)
        )
    }

    public func changeStoryType(
        at position: Double,
        to type: StoryType,
        in stories: [Double: StoryStep],
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

    /// Splits the step at every line break of its text. The focus goes to the line that holds
    /// the cursor.
    public func onLineBreak(_ lineBreak: Action.LineBreak, in stories: [Double: StoryStep]) -> StoryState {
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
                position: 0
            )
        }

        var ordered = orderedSteps(stories)
        guard let index = ordered.firstIndex(where: { $0.position == lineBreak.position }) else {
            return StoryState(stories: stories)
        }

        ordered[index].step = first
        ordered.insert(contentsOf: newSteps.map { (position: 0, step: $0) }, at: index + 1)

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

        let renumbered = renumber(ordered.map(\.step))
        let focus = Double(index + focusLine)
        return StoryState(
            stories: renumbered,
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
    public func onErase(_ erase: Action.EraseStory, in stories: [Double: StoryStep]) -> StoryState? {
        let step = erase.storyStep

        if step.isListLike {
            var newStep = step
            newStep.type = .text
            newStep.checked = nil
            var newStories = stories
            newStories[erase.position] = newStep
            return StoryState(stories: newStories, lastEdit: .whole, focus: erase.position)
        }

        if !step.isTitle, step.tags.contains(where: { Self.erasableTags.contains($0.tag) }) {
            var newStep = step
            newStep.tags.removeAll { Self.erasableTags.contains($0.tag) }
            var newStories = stories
            newStories[erase.position] = newStep
            return StoryState(stories: newStories, lastEdit: .whole, focus: erase.position)
        }

        guard !step.isTitle else { return nil }

        var ordered = orderedSteps(stories)
        guard let index = ordered.firstIndex(where: { $0.position == erase.position }), index > 0 else {
            return nil
        }

        let previous = ordered[index - 1].step

        if !previous.isTextStep {
            ordered.remove(at: index - 1)
            let focus = Double(index - 1)
            return StoryState(
                stories: renumber(ordered.map(\.step)),
                lastEdit: .whole,
                focus: focus,
                selection: .cursor(0, at: focus)
            )
        }

        let previousText = previous.text ?? ""
        let previousLength = previousText.utf16.count
        var merged = previous
        merged.text = previousText + (step.text ?? "")
        merged.spans = previous.spans + SpansHandler.shift(step.spans, by: previousLength)

        ordered[index - 1].step = merged
        ordered.remove(at: index)

        let focus = Double(index - 1)
        return StoryState(
            stories: renumber(ordered.map(\.step)),
            lastEdit: .whole,
            focus: focus,
            selection: .cursor(previousLength, at: focus)
        )
    }

    public func onDelete(_ delete: Action.DeleteStory, in stories: [Double: StoryStep]) -> StoryState {
        var newStories = stories
        newStories.removeValue(forKey: delete.position)
        let previousFocus = focusHandler.findPreviousFocus(before: delete.position, in: newStories)

        let ordered = orderedSteps(newStories)
        let focus = previousFocus.flatMap { position in ordered.firstIndex { $0.position == position } }.map(Double.init)
        let cursor = focus.flatMap { ordered[Int($0)].step.text?.utf16.count } ?? 0

        return StoryState(
            stories: renumber(ordered.map(\.step)),
            lastEdit: .whole,
            focus: focus,
            selection: focus.map { .cursor(cursor, at: $0) }
        )
    }

    /// Moves a step to right after another one. The title always stays first.
    public func move(_ move: Action.Move, in stories: [Double: StoryStep]) -> StoryState? {
        guard move.positionFrom != move.positionTo,
              let step = stories[move.positionFrom],
              !step.isTitle,
              stories[move.positionTo] != nil
        else { return nil }

        var ordered = orderedSteps(stories)
        guard let fromIndex = ordered.firstIndex(where: { $0.position == move.positionFrom }) else { return nil }
        let moving = ordered.remove(at: fromIndex)

        guard let targetIndex = ordered.firstIndex(where: { $0.position == move.positionTo }) else { return nil }
        ordered.insert(moving, at: targetIndex + 1)

        let newIndex = Double(targetIndex + 1)
        return StoryState(stories: renumber(ordered.map(\.step)), lastEdit: .whole, focus: newIndex)
    }

    public func add(_ step: StoryStep, after position: Double?, in stories: [Double: StoryStep]) -> StoryState {
        var ordered = orderedSteps(stories)
        let index = position.flatMap { position in ordered.firstIndex { $0.position == position } }.map { $0 + 1 } ?? ordered.count
        ordered.insert((position: 0, step: step), at: index)
        let focus = Double(index)
        return StoryState(
            stories: renumber(ordered.map(\.step)),
            lastEdit: .whole,
            focus: step.isTextStep ? focus : nil,
            selection: .cursor(0, at: focus)
        )
    }

    // MARK: - Helpers

    private func orderedSteps(_ stories: [Double: StoryStep]) -> [(position: Double, step: StoryStep)] {
        stories.keys.sorted().map { ($0, stories[$0]!) }
    }

    /// Positions 0, 1, 2... in the given order, stored in each step as well.
    public func renumber(_ steps: [StoryStep]) -> [Double: StoryStep] {
        var result: [Double: StoryStep] = [:]
        for (index, step) in steps.enumerated() {
            var step = step
            step.position = Double(index)
            result[Double(index)] = step
        }
        return result
    }
}
