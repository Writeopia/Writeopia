import Foundation
import Observation
import Writeopia
import WrModels

/// Asks the text view of a step to take the keyboard and put the cursor at `cursor`.
public struct FocusRequest: Equatable {
    public let id = UUID()
    public let stepId: String
    public let cursor: Int
}

/// Holds the document being edited and turns UI events into `WriteopiaManager` calls.
/// Mirrors `WriteopiaStateManager` of the Kotlin SDK.
@Observable
public final class WriteopiaStateManager {
    public static let dragPayloadPrefix = "writeopia-step:"

    public private(set) var currentStory: StoryState = .empty
    public var isEditable = true
    /// Position after which a dragged step would be dropped.
    public private(set) var dragPosition: Double?
    public private(set) var isDragging = false
    public private(set) var focusRequest: FocusRequest?
    /// Increases on every change of the document, handy to observe edits.
    public private(set) var changeCount = 0

    @ObservationIgnored public var onDocumentLinkClick: ((DocumentLink) -> Void)?
    @ObservationIgnored private let writeopiaManager: WriteopiaManager

    public init(writeopiaManager: WriteopiaManager = WriteopiaManager()) {
        self.writeopiaManager = writeopiaManager
    }

    /// What the editor draws, spaces included.
    public var toDraw: [DrawStory] {
        StepsModifier.modify(currentStory, dragPosition: dragPosition)
    }

    public var title: String { writeopiaManager.title(currentStory) }

    /// The document content, ready to be stored.
    public var documentContent: [StoryStep] { writeopiaManager.documentContent(currentStory) }

    // MARK: - Document

    public func loadDocument(_ document: WrDocument) {
        currentStory = writeopiaManager.loadDocument(document)
        focusRequest = nil
    }

    public func newDocument() {
        apply(writeopiaManager.newDocument())
    }

    // MARK: - Text edition

    /// Called by the text view of a step on every edit. Line breaks split the step.
    public func handleTextInput(_ text: String, cursor: Int, stepId: String) {
        guard isEditable, let (position, step) = find(stepId) else { return }

        var newStep = step
        newStep.spans = SpansHandler.adjust(step.spans, from: step.text ?? "", to: text)
        newStep.text = text

        if text.contains("\n") {
            apply(writeopiaManager.onLineBreak(
                Action.LineBreak(storyStep: newStep, position: position, cursor: cursor),
                state: currentStory
            ))
        } else {
            apply(writeopiaManager.changeStoryState(
                Action.StoryStateChange(storyStep: newStep, position: position, selectionStart: cursor, selectionEnd: cursor),
                state: currentStory
            ))
        }
    }

    /// Backspace with the cursor at the start of a step.
    public func onErase(stepId: String) {
        guard isEditable, let (position, step) = find(stepId) else { return }
        apply(writeopiaManager.onErase(Action.EraseStory(storyStep: step, position: position), state: currentStory))
    }

    public func onFocusChange(stepId: String, hasFocus: Bool) {
        guard let (position, _) = find(stepId) else { return }
        if hasFocus {
            currentStory.focus = position
        } else if currentStory.focus == position {
            currentStory.focus = nil
        }
    }

    public func onCheckedChange(stepId: String, checked: Bool) {
        guard isEditable, let (position, _) = find(stepId) else { return }
        currentStory = writeopiaManager.checkItem(at: position, checked: checked, state: currentStory)
        changeCount += 1
    }

    /// Tap below the last step.
    public func clickAtTheEnd() {
        guard isEditable else { return }
        apply(writeopiaManager.clickAtTheEnd(state: currentStory))
    }

    public func focusRequestHandled(_ request: FocusRequest) {
        if focusRequest?.id == request.id {
            focusRequest = nil
        }
    }

    // MARK: - Drag and drop

    public func dragPayload(for step: StoryStep) -> String {
        Self.dragPayloadPrefix + step.id
    }

    public func onDragHover(_ position: Double?) {
        dragPosition = position
        isDragging = position != nil
    }

    public func onDragStop() {
        dragPosition = nil
        isDragging = false
    }

    /// Moves the dragged step right after the step at `position`. Returns false for payloads
    /// that aren't steps of this document.
    @discardableResult
    public func moveRequest(payload: String, after position: Double) -> Bool {
        defer { onDragStop() }

        guard isEditable,
              payload.hasPrefix(Self.dragPayloadPrefix),
              let (from, _) = find(String(payload.dropFirst(Self.dragPayloadPrefix.count)))
        else { return false }

        var moved = writeopiaManager.moveRequest(Action.Move(positionFrom: from, positionTo: position), state: currentStory)
        guard moved != currentStory else { return false }

        // Moving shouldn't open the keyboard.
        moved.focus = nil
        currentStory = moved
        changeCount += 1
        return true
    }

    // MARK: - Private

    private func find(_ stepId: String) -> (Double, StoryStep)? {
        currentStory.stories.first { $0.value.id == stepId }.map { ($0.key, $0.value) }
    }

    private func apply(_ newState: StoryState) {
        guard newState != currentStory else { return }

        let isStructural = newState.lastEdit == .whole
        currentStory = newState
        changeCount += 1

        // Structural changes move the focus to another text view; typing keeps it where it is.
        if isStructural, let focus = newState.focus, let step = newState.stories[focus] {
            let cursor = newState.selection.position == focus ? newState.selection.end : 0
            focusRequest = FocusRequest(stepId: step.id, cursor: cursor)
        }
    }
}
