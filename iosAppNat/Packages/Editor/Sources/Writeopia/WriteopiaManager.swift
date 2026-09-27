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
