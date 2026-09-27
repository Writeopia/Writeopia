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

/// Text selected (or the cursor, when `start == end`) inside a step. Offsets are UTF-16.
public struct StepSelection: Equatable {
    public let stepId: String
    public let start: Int
    public let end: Int

    public var isEmpty: Bool { start == end }
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
    /// Lines selected by sliding them sideways. Kept by id so they survive renumbering.
    public private(set) var selectedStepIds: Set<String> = []
    /// Current selection of the focused text step. Drives the formatting buttons.
    public private(set) var textSelection: StepSelection?
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
            if textSelection?.stepId == stepId {
                textSelection = nil
            }
        }
    }

    public func onSelectionChange(stepId: String, start: Int, end: Int) {
        let selection = StepSelection(stepId: stepId, start: min(start, end), end: max(start, end))
        if textSelection != selection {
            textSelection = selection
        }
    }

    /// Toggles `span` on the selected lines when there are any, otherwise on the selected text.
    public func toggleSpan(_ span: Span) {
        if !selectedPositions.isEmpty {
            guard isEditable else { return }
            let newState = writeopiaManager.toggleSpanOnSteps(span, positions: selectedPositions, state: currentStory)
            if newState != currentStory {
                currentStory = newState
                changeCount += 1
            }
            return
        }

        guard let selection = textSelection else { return }
        updateSelection(selection) { position, state in
            writeopiaManager.toggleSpan(span, at: position, start: selection.start, end: selection.end, state: state)
        }
    }

    /// Links `selection` to `url`, or removes its link when `url` is nil. The selection is passed
    /// in because asking for the URL takes the focus away from the text.
    public func setLink(_ url: String?, for selection: StepSelection) {
        updateSelection(selection) { position, state in
            writeopiaManager.setLink(url, at: position, start: selection.start, end: selection.end, state: state)
        }
    }

    /// Whether the selected lines, or else the selected text, already have `span`, to show its
    /// button as active.
    public func isSpanActive(_ span: Span) -> Bool {
        if !selectedPositions.isEmpty {
            return writeopiaManager.isSpanOnSteps(span, positions: selectedPositions, state: currentStory)
        }
        guard let selection = textSelection, !selection.isEmpty, let step = step(withId: selection.stepId) else {
            return false
        }
        return SpansHandler.isFullyCovered(step.spans, span: span.rawValue, start: selection.start, end: selection.end)
    }

    /// Whether any highlight color covers the whole selection.
    public var isHighlightActive: Bool {
        Span.highlights.contains(where: isSpanActive)
    }

    private func updateSelection(_ selection: StepSelection, change: (Double, StoryState) -> StoryState) {
        guard isEditable, !selection.isEmpty, let (position, _) = find(selection.stepId) else { return }
        let newState = change(position, currentStory)
        guard newState != currentStory else { return }
        currentStory = newState
        changeCount += 1
    }

    // MARK: - Line selection

    public var hasSelectedLines: Bool { !selectedPositions.isEmpty }

    /// Positions of the selected lines still in the document, in order.
    public var selectedPositions: [Double] {
        guard !selectedStepIds.isEmpty else { return [] }
        return currentStory.sortedPositions.filter { position in
            currentStory.stories[position].map { selectedStepIds.contains($0.id) } == true
        }
    }

    public func isSelected(stepId: String) -> Bool {
        selectedStepIds.contains(stepId)
    }

    /// Selects or unselects a line, like `onSelected` of the SDK. The title can't be selected.
    public func onSelected(stepId: String, isSelected: Bool) {
        guard isEditable, let step = step(withId: stepId), !step.isTitle else { return }
        if isSelected {
            selectedStepIds.insert(stepId)
        } else {
            selectedStepIds.remove(stepId)
        }
    }

    public func toggleLineSelection(stepId: String) {
        onSelected(stepId: stepId, isSelected: !isSelected(stepId: stepId))
    }

    public func clearLineSelection() {
        selectedStepIds.removeAll()
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

    // MARK: - Content added by the app (AI answers)

    /// Text of the whole document, used by AI commands on the document.
    public var documentText: String { writeopiaManager.documentText(currentStory) }

    /// Position of the last step.
    public var lastPosition: Double? { currentStory.sortedPositions.last }

    /// The step with the cursor, or the last text step when nothing has the focus.
    public var currentTextStep: (position: Double, step: StoryStep)? {
        if let focus = currentStory.focus, let step = currentStory.stories[focus], step.isTextStep {
            return (focus, step)
        }
        return currentStory.sortedPositions.reversed()
            .compactMap { position in currentStory.stories[position].map { (position, $0) } }
            .first { $0.1.isTextStep && !$0.1.isTitle && !($0.1.text ?? "").isEmpty }
    }

    /// Adds a loading step after `position` and returns its id, so it can later become the
    /// answer. Mirrors `loadingAtPosition` of the SDK.
    @discardableResult
    public func loadingAtPosition(_ position: Double?) -> String {
        let loading = StoryStep(type: .loading, position: 0)
        currentStory = writeopiaManager.addAtPosition(loading, after: position, state: currentStory)
        currentStory.focus = nil
        changeCount += 1
        return loading.id
    }

    /// Shows `text` as an AI answer in the step with `stepId` (the loading step at first).
    public func showAiAnswer(_ text: String, stepId: String) {
        guard var step = step(withId: stepId) else { return }
        step.type = .aiAnswer
        step.text = text
        step.spans = []
        currentStory = writeopiaManager.replaceStep(step, state: currentStory)
        changeCount += 1
    }

    public func removeStep(stepId: String) {
        currentStory = writeopiaManager.removeStep(id: stepId, state: currentStory)
        changeCount += 1
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

    /// The current state of the step with `stepId`, if it's still in the document.
    public func step(withId stepId: String) -> StoryStep? {
        find(stepId)?.1
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
