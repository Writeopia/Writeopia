import Foundation
import WrModels

/// Stateless editing API: every call receives the current `StoryState` and returns the next
/// one. Mirrors `WriteopiaManager` of the Kotlin SDK; `WriteopiaStateManager` holds the state.
public struct WriteopiaManager {
    private let contentManager: ContentManager
    private let focusHandler: FocusHandler

    public init(contentManager: ContentManager = ContentManager(), focusHandler: FocusHandler = FocusHandler()) {
        self.contentManager = contentManager
        self.focusHandler = focusHandler
    }

    /// A document with an empty title, focused.
    public func newDocument() -> StoryState {
        let stories = contentManager.renumber([StoryStep(type: .title, text: "", position: 0)])
        return StoryState(stories: stories, lastEdit: .nothing, focus: 0)
    }

    /// Prepares the steps of a document for edition: sorted, renumbered, without ephemeral steps
    /// and always starting with a title (documents created elsewhere may not have one).
    public func loadDocument(_ document: WrDocument) -> StoryState {
        var steps = document.content
            .sorted { $0.position < $1.position }
            .filter { !StoryTypes.ephemeral.contains($0.type.number) }

        if let titleIndex = steps.firstIndex(where: \.isTitle) {
            let title = steps.remove(at: titleIndex)
            steps.insert(title, at: 0)
        } else {
            steps.insert(StoryStep(type: .title, text: document.title, position: 0), at: 0)
        }

        return StoryState(stories: contentManager.renumber(steps), lastEdit: .nothing, focus: nil)
    }

    public func changeStoryState(_ change: Action.StoryStateChange, state: StoryState) -> StoryState {
        contentManager.changeStoryState(change, in: state.stories) ?? state
    }

    public func changeStoryType(at position: Double, to type: StoryType, state: StoryState) -> StoryState {
        let cursor = state.selection.position == position ? state.selection.end : 0
        return contentManager.changeStoryType(at: position, to: type, in: state.stories, cursor: cursor) ?? state
    }

    public func onLineBreak(_ lineBreak: Action.LineBreak, state: StoryState) -> StoryState {
        contentManager.onLineBreak(lineBreak, in: state.stories)
    }

    public func onErase(_ erase: Action.EraseStory, state: StoryState) -> StoryState {
        contentManager.onErase(erase, in: state.stories) ?? state
    }

    public func onDelete(_ delete: Action.DeleteStory, state: StoryState) -> StoryState {
        contentManager.onDelete(delete, in: state.stories)
    }

    public func moveRequest(_ move: Action.Move, state: StoryState) -> StoryState {
        contentManager.move(move, in: state.stories) ?? state
    }

    /// Toggles an inline span on `start..<end` of the step at `position`. Highlights replace
    /// each other: applying a color removes the other colors from the range.
    public func toggleSpan(_ span: Span, at position: Double, start: Int, end: Int, state: StoryState) -> StoryState {
        updateSpans(at: position, start: start, end: end, state: state) { spans, start, end in
            var spans = spans
            let isAdding = !SpansHandler.isFullyCovered(spans, span: span.rawValue, start: start, end: end)
            if span.isHighlight, isAdding {
                for other in Span.highlights where other != span {
                    spans = SpansHandler.remove(other.rawValue, start: start, end: end, from: spans)
                }
            }
            return SpansHandler.toggle(span.rawValue, start: start, end: end, in: spans)
        }
    }

    /// Whether the whole text of every step at `positions` has `span`. Empty steps are ignored.
    public func isSpanOnSteps(_ span: Span, positions: [Double], state: StoryState) -> Bool {
        let steps = positions.compactMap { state.stories[$0] }.filter { $0.isTextStep && !($0.text ?? "").isEmpty }
        guard !steps.isEmpty else { return false }
        return steps.allSatisfy { step in
            SpansHandler.isFullyCovered(step.spans, span: span.rawValue, start: 0, end: step.text?.utf16.count ?? 0)
        }
    }

    /// Toggles `span` on the whole text of the steps at `positions`, like the formatting buttons
    /// of the SDK when lines are selected: if all of them have it, it's removed from all.
    public func toggleSpanOnSteps(_ span: Span, positions: [Double], state: StoryState) -> StoryState {
        let shouldRemove = isSpanOnSteps(span, positions: positions, state: state)
        var newState = state

        for position in positions {
            guard var step = newState.stories[position], step.isTextStep else { continue }
            let length = step.text?.utf16.count ?? 0
            guard length > 0 else { continue }

            if shouldRemove {
                step.spans = SpansHandler.remove(span.rawValue, start: 0, end: length, from: step.spans)
            } else {
                if span.isHighlight {
                    for other in Span.highlights where other != span {
                        step.spans = SpansHandler.remove(other.rawValue, start: 0, end: length, from: step.spans)
                    }
                }
                step.spans = SpansHandler.remove(span.rawValue, start: 0, end: length, from: step.spans) +
                    [SpanInfo(start: 0, end: length, span: span.rawValue)]
            }
            newState.stories[position] = step
        }

        if newState != state {
            newState.lastEdit = .whole
        }
        return newState
    }

    // MARK: - Selected lines (the SDK's edition menu)

    /// Changes each step at `positions` to `type`, or back to a paragraph when it already is of
    /// that type, like `toggleStateForStories` of the SDK. The title never changes.
    public func toggleType(_ type: StoryType, positions: [Double], state: StoryState) -> StoryState {
        mapSteps(positions, state: state) { step in
            guard step.isTextStep else { return step }
            var step = step
            step.type = step.type.number == type.number ? .text : type
            step.checked = step.type.number == StoryType.checkItem.number ? (step.checked ?? false) : nil
            return step
        }
    }

    /// Adds `tag` to each step at `positions`, or removes it from the steps that have it, like
    /// `toggleTagForStories` of the SDK.
    public func toggleTag(_ tag: BlockTag, positions: [Double], state: StoryState) -> StoryState {
        mapSteps(positions, state: state) { step in
            var step = step
            if step.hasTag(tag.rawValue) {
                step.tags.removeAll { $0.tag == tag.rawValue }
            } else {
                step.tags.append(TagInfo(tag: tag.rawValue))
            }
            return step
        }
    }

    /// Makes each step at `positions` a heading of `tag`'s level, replacing other levels, or a
    /// regular paragraph when it already is. Mirrors `addTitle` of the SDK.
    public func toggleHeading(_ tag: BlockTag, positions: [Double], state: StoryState) -> StoryState {
        guard tag.isHeading else { return state }
        return mapSteps(positions, state: state) { step in
            guard step.isTextStep else { return step }
            var step = step
            let shouldRemove = step.hasTag(tag.rawValue)
            step.tags.removeAll { info in BlockTag(rawValue: info.tag)?.isHeading == true }
            if !shouldRemove {
                step.tags.append(TagInfo(tag: tag.rawValue))
            }
            return step
        }
    }

    /// Deletes the steps at `positions`, except the title.
    public func deleteSteps(_ positions: [Double], state: StoryState) -> StoryState {
        let toDelete = Set(positions)
        let remaining = state.sortedPositions
            .filter { position in !toDelete.contains(position) || state.stories[position]?.isTitle == true }
            .compactMap { state.stories[$0] }
        guard remaining.count != state.stories.count else { return state }
        return StoryState(stories: contentManager.renumber(remaining), lastEdit: .whole, focus: nil)
    }

    /// Text of the steps at `positions`, one per line, like `copySelection` of the SDK.
    public func text(of positions: [Double], state: StoryState) -> String {
        positions.sorted()
            .compactMap { state.stories[$0] }
            .filter { $0.isTextStep || $0.type.number == StoryType.documentLink.number }
            .compactMap(\.text)
            .joined(separator: "\n")
    }

    /// Adds a link to the document `documentId` right after `position`.
    public func addDocumentLink(after position: Double, documentId: String, title: String, state: StoryState) -> StoryState {
        let link = StoryStep(
            type: .documentLink,
            text: title,
            position: 0,
            documentLink: DocumentLink(id: documentId, title: title)
        )
        var newState = contentManager.add(link, after: position, in: state.stories)
        newState.focus = nil
        return newState
    }

    private func mapSteps(_ positions: [Double], state: StoryState, change: (StoryStep) -> StoryStep) -> StoryState {
        var newState = state
        for position in positions {
            guard let step = newState.stories[position], !step.isTitle else { continue }
            newState.stories[position] = change(step)
        }
        if newState != state {
            newState.lastEdit = .whole
        }
        return newState
    }

    /// Links `start..<end` of the step at `position` to `url`, or removes the link when `url` is nil.
    public func setLink(_ url: String?, at position: Double, start: Int, end: Int, state: StoryState) -> StoryState {
        updateSpans(at: position, start: start, end: end, state: state) { spans, start, end in
            if let url {
                SpansHandler.setLink(url, start: start, end: end, in: spans)
            } else {
                SpansHandler.remove(Span.link.rawValue, start: start, end: end, from: spans)
            }
        }
    }

    private func updateSpans(
        at position: Double,
        start: Int,
        end: Int,
        state: StoryState,
        change: ([SpanInfo], Int, Int) -> [SpanInfo]
    ) -> StoryState {
        guard var step = state.stories[position], step.isTextStep else { return state }
        let length = step.text?.utf16.count ?? 0
        let clampedStart = max(0, min(start, length))
        let clampedEnd = max(clampedStart, min(end, length))
        guard clampedStart < clampedEnd else { return state }

        step.spans = change(step.spans, clampedStart, clampedEnd)

        var newState = state
        newState.stories[position] = step
        newState.lastEdit = .lineEdition(position: position, storyStep: step)
        return newState
    }

    public func checkItem(at position: Double, checked: Bool, state: StoryState) -> StoryState {
        guard var step = state.stories[position] else { return state }
        step.checked = checked

        var newState = state
        newState.stories[position] = step
        newState.lastEdit = .lineEdition(position: position, storyStep: step)
        return newState
    }

    /// Moves the focus to the next text step, keeping the cursor.
    public func nextFocus(after position: Double, cursor: Int, state: StoryState) -> StoryState {
        guard let next = focusHandler.findNextFocus(after: position, in: state.stories) else { return state }
        var newState = state
        newState.focus = next
        newState.selection = .cursor(cursor, at: next)
        return newState
    }

    /// Tap below the last step: focus the last step when it's an empty paragraph, otherwise add one.
    public func clickAtTheEnd(state: StoryState) -> StoryState {
        guard let lastPosition = state.sortedPositions.last, let last = state.stories[lastPosition] else {
            return newDocument()
        }

        if last.type.number == StoryType.text.number, (last.text ?? "").isEmpty {
            var newState = state
            newState.focus = lastPosition
            newState.selection = .cursor(0, at: lastPosition)
            return newState
        }

        return contentManager.add(StoryStep(type: .text, text: "", position: 0), after: lastPosition, in: state.stories)
    }

    /// Adds `step` right after `position`, or at the end when `position` is nil.
    public func addAtPosition(_ step: StoryStep, after position: Double?, state: StoryState) -> StoryState {
        contentManager.add(step, after: position, in: state.stories)
    }

    /// Replaces the step with the same id as `step`, keeping its position.
    public func replaceStep(_ step: StoryStep, state: StoryState) -> StoryState {
        guard let position = state.stories.first(where: { $0.value.id == step.id })?.key else { return state }
        var newState = state
        var updated = step
        updated.position = position
        newState.stories[position] = updated
        newState.lastEdit = .lineEdition(position: position, storyStep: updated)
        return newState
    }

    public func removeStep(id: String, state: StoryState) -> StoryState {
        guard let position = state.stories.first(where: { $0.value.id == id })?.key else { return state }
        var newState = contentManager.onDelete(Action.DeleteStory(position: position), in: state.stories)
        newState.focus = nil
        return newState
    }

    /// Text of every text step, one per line. Used as the input of AI commands on the document.
    public func documentText(_ state: StoryState) -> String {
        state.sortedStories
            .filter(\.isTextStep)
            .compactMap(\.text)
            .filter { !$0.isEmpty }
            .joined(separator: "\n")
    }

    /// The steps that make the document content: sorted, without ephemeral steps.
    public func documentContent(_ state: StoryState) -> [StoryStep] {
        state.sortedStories.filter { !StoryTypes.ephemeral.contains($0.type.number) }
    }

    /// The text of the title step.
    public func title(_ state: StoryState) -> String {
        state.sortedStories.first(where: \.isTitle)?.text ?? ""
    }
}
