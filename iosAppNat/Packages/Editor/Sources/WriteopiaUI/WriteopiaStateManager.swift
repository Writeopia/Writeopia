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
    /// False while the document is locked: text, checkboxes, dragging and selection are disabled.
    public var isEditable = true
    public var fontFamily: EditorFont = .system
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
    /// Asks the AI to write the content of the heading with this step id, like the AI wand of
    /// the Compose app. Nil when no AI is available, which hides the wand.
    @ObservationIgnored public var onGenerateSection: ((String) -> Void)?
    /// Called with the id of the last line that just became a list item or a checkbox, so the AI
    /// can suggest the next items, like `generateSuggestionsList` of the SDK.
    @ObservationIgnored public var onListStarted: ((String) -> Void)?
    @ObservationIgnored private let writeopiaManager: WriteopiaManager

    public init(writeopiaManager: WriteopiaManager = WriteopiaManager()) {
        self.writeopiaManager = writeopiaManager
    }

    /// Step types drawn by the app through `CustomStepDrawers` (drawings, for instance).
    public var customDrawableTypes: Set<Int> = []

    /// What the editor draws, spaces included.
    public var toDraw: [DrawStory] {
        StepsModifier.modify(currentStory, dragPosition: dragPosition, extraTypes: customDrawableTypes)
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
            dismissAiSuggestions()
            guard let (position, _) = find(stepId) else { return }
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
        dismissAiSuggestions()
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

    /// Checkbox, list item and code block buttons of the selection menu.
    public func toggleTypeOfSelectedLines(_ type: StoryType) {
        let lastId = selectedPositions.last.flatMap { currentStory.stories[$0]?.id }
        applyToSelectedLines { writeopiaManager.toggleType(type, positions: $0, state: $1) }

        let isList = type.number == StoryType.checkItem.number || type.number == StoryType.unorderedListItem.number
        if isList, let lastId, step(withId: lastId)?.type.number == type.number {
            onListStarted?(lastId)
        }
    }

    /// Box and card buttons of the selection menu.
    public func toggleTagOfSelectedLines(_ tag: BlockTag) {
        applyToSelectedLines { writeopiaManager.toggleTag(tag, positions: $0, state: $1) }
    }

    /// Title, subtitle and header buttons of the selection menu.
    public func toggleHeadingOfSelectedLines(_ tag: BlockTag) {
        applyToSelectedLines { writeopiaManager.toggleHeading(tag, positions: $0, state: $1) }
    }

    /// Whether every selected line is of `type`, to show the button as active.
    public func selectedLinesAre(_ type: StoryType) -> Bool {
        let steps = selectedPositions.compactMap { currentStory.stories[$0] }
        return !steps.isEmpty && steps.allSatisfy { $0.type.number == type.number }
    }

    /// Whether every selected line has `tag`, to show the button as active.
    public func selectedLinesHave(_ tag: BlockTag) -> Bool {
        let steps = selectedPositions.compactMap { currentStory.stories[$0] }
        return !steps.isEmpty && steps.allSatisfy { $0.hasTag(tag.rawValue) }
    }

    // MARK: - Blocks: the selected lines, or else the focused line

    /// Where the block buttons of the side menu act: the selected lines when there are any,
    /// otherwise the line with the cursor, like the text options of the desktop app.
    public var blockPositions: [Double] {
        if !selectedPositions.isEmpty { return selectedPositions }
        guard let focus = currentStory.focus, let step = currentStory.stories[focus], !step.isTitle else { return [] }
        return [focus]
    }

    /// Checkbox, list item and code block buttons of the side menu.
    public func toggleType(_ type: StoryType) {
        applyToBlocks { writeopiaManager.toggleType(type, positions: $0, state: $1) }
    }

    /// Box and card buttons of the side menu.
    public func toggleTag(_ tag: BlockTag) {
        applyToBlocks { writeopiaManager.toggleTag(tag, positions: $0, state: $1) }
    }

    /// Title, subtitle and header buttons of the side menu.
    public func toggleHeading(_ tag: BlockTag) {
        applyToBlocks { writeopiaManager.toggleHeading(tag, positions: $0, state: $1) }
    }

    /// Whether every block is of `type`, to show the button as active.
    public func blocksAre(_ type: StoryType) -> Bool {
        let steps = blockPositions.compactMap { currentStory.stories[$0] }
        return !steps.isEmpty && steps.allSatisfy { $0.type.number == type.number }
    }

    /// Whether every block has `tag`, to show the button as active.
    public func blocksHave(_ tag: BlockTag) -> Bool {
        let steps = blockPositions.compactMap { currentStory.stories[$0] }
        return !steps.isEmpty && steps.allSatisfy { $0.hasTag(tag.rawValue) }
    }

    /// Text of the selected lines, one per line.
    public var selectedLinesText: String {
        writeopiaManager.text(of: selectedPositions, state: currentStory)
    }

    /// The selected lines with their text and spans, for the clipboard.
    public var selectedLines: [StoryStep] {
        selectedPositions.compactMap { currentStory.stories[$0] }
    }

    /// Deletes the selected lines and clears the selection, like `deleteSelection` of the SDK.
    public func deleteSelectedLines() {
        guard isEditable else { return }
        let newState = writeopiaManager.deleteSteps(selectedPositions, state: currentStory)
        selectedStepIds.removeAll()
        guard newState != currentStory else { return }
        currentStory = newState
        changeCount += 1
    }

    /// Adds a link to another document right after the last selected line.
    public func addDocumentLinkAfterSelection(documentId: String, title: String) {
        guard isEditable, let position = selectedPositions.last else { return }
        currentStory = writeopiaManager.addDocumentLink(after: position, documentId: documentId, title: title, state: currentStory)
        changeCount += 1
    }

    private func applyToSelectedLines(_ change: ([Double], StoryState) -> StoryState) {
        apply(change, to: selectedPositions)
    }

    private func applyToBlocks(_ change: ([Double], StoryState) -> StoryState) {
        apply(change, to: blockPositions)
    }

    private func apply(_ change: ([Double], StoryState) -> StoryState, to positions: [Double]) {
        guard isEditable, !positions.isEmpty else { return }
        let newState = change(positions, currentStory)
        guard newState != currentStory else { return }
        currentStory = newState
        changeCount += 1
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
        streamingAnswerId = loading.id
        return loading.id
    }

    /// The AI answer still being written; it can't be accepted yet.
    public private(set) var streamingAnswerId: String?

    /// The answer with `stepId` is complete (or failed).
    public func aiAnswerFinished(stepId: String) {
        if streamingAnswerId == stepId { streamingAnswerId = nil }
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

    /// Turns the AI answer with `stepId` into regular lines, reading its Markdown (headings,
    /// lists, checkboxes), like the accept button of `AiAnswerDrawer` of the SDK.
    public func acceptAiAnswer(stepId: String) {
        guard isEditable, stepId != streamingAnswerId,
              let step = step(withId: stepId), step.type.number == StoryType.aiAnswer.number
        else { return }
        var lines = MarkdownSteps.parse(step.text ?? "")
        if lines.isEmpty {
            lines = [StoryStep(type: .text, text: step.text ?? "", position: 0)]
        }
        currentStory = writeopiaManager.replaceStep(id: stepId, with: lines, state: currentStory)
        changeCount += 1
    }

    // MARK: - AI list suggestions

    public var hasAiSuggestions: Bool {
        currentStory.stories.values.contains(where: \.isAiSuggestion)
    }

    /// Shows `items` as suggestions right after the list item with `stepId`, of the same type.
    /// They are gray and not saved until accepted. Replaces suggestions shown before.
    public func showAiSuggestions(_ items: [String], after stepId: String) {
        guard isEditable, !items.isEmpty else { return }
        dismissAiSuggestions()
        guard let (position, listStep) = find(stepId) else { return }

        let suggestions = items.enumerated().map { index, item in
            StoryStep(
                type: listStep.type,
                text: item,
                checked: listStep.type.number == StoryType.checkItem.number ? false : nil,
                tags: [TagInfo(tag: AiTag.suggestion)] + (index == 0 ? [TagInfo(tag: AiTag.firstSuggestion)] : []),
                position: 0
            )
        }
        currentStory = writeopiaManager.addSteps(suggestions, after: position, state: currentStory)
        changeCount += 1
    }

    /// Keeps the suggestions as regular list items, like `acceptSuggestions` of the SDK.
    public func acceptAiSuggestions() {
        guard hasAiSuggestions else { return }
        var state = currentStory
        for (position, step) in state.stories where step.isAiSuggestion {
            var accepted = step
            accepted.tags.removeAll { $0.tag == AiTag.suggestion || $0.tag == AiTag.firstSuggestion }
            state.stories[position] = accepted
        }
        state.lastEdit = .whole
        currentStory = state
        changeCount += 1
    }

    /// Removes the suggestions, like `cancelSuggestions` of the SDK.
    public func dismissAiSuggestions() {
        guard hasAiSuggestions else { return }
        currentStory = writeopiaManager.removeSteps(where: \.isAiSuggestion, state: currentStory)
        changeCount += 1
    }

    /// Text of the heading with `stepId`, when it's a heading the AI can write a section for.
    public func headingText(stepId: String) -> String? {
        guard let step = step(withId: stepId), step.headingLevel != nil, !step.isTitle else { return nil }
        let text = (step.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? nil : text
    }

    /// Position of the step with `stepId`.
    public func position(of stepId: String) -> Double? {
        find(stepId)?.0
    }

    // MARK: - Images

    /// Image steps whose upload is still running; their drawer shows a progress indicator.
    public private(set) var uploadingStepIds: Set<String> = []

    /// Adds an image where the cursor is, like `addImage` of the SDK: after the title, in place
    /// of an empty line, after any other line, or at the end when nothing has the focus.
    /// Returns the id of the image step.
    @discardableResult
    public func addImage(path: String?, url: String? = nil, uploading: Bool = false) -> String? {
        guard isEditable else { return nil }
        let image = StoryStep(type: .image, url: url, path: path, position: 0)

        if let focus = currentStory.focus, let current = currentStory.stories[focus] {
            if !current.isTitle, current.type.number == StoryType.text.number, (current.text ?? "").isEmpty {
                var replaced = image
                replaced.position = focus
                currentStory.stories[focus] = replaced
                currentStory.lastEdit = .whole
            } else {
                currentStory = writeopiaManager.addAtPosition(image, after: focus, state: currentStory)
            }
        } else {
            currentStory = writeopiaManager.addAtPosition(image, after: lastPosition, state: currentStory)
        }

        currentStory.focus = nil
        if uploading {
            uploadingStepIds.insert(image.id)
        }
        changeCount += 1
        return image.id
    }

    /// The upload of an image finished: it now points to `url` instead of the local file.
    /// With a nil `url` (upload failed) the image keeps its local path, like the SDK.
    public func imageUploadFinished(stepId: String, url: String?) {
        uploadingStepIds.remove(stepId)
        guard let url, var step = step(withId: stepId) else { return }
        step.url = url
        step.path = nil
        currentStory = writeopiaManager.replaceStep(step, state: currentStory)
        changeCount += 1
    }

    /// Adds `step` at the end of the document, like new drawings in the SDK.
    public func addAtTheEnd(_ step: StoryStep) {
        guard isEditable else { return }
        currentStory = writeopiaManager.addAtPosition(step, after: lastPosition, state: currentStory)
        currentStory.focus = nil
        changeCount += 1
    }

    /// Replaces the text of a step, keeping its type and position (e.g. an edited drawing).
    public func updateText(_ text: String, stepId: String) {
        guard isEditable, var step = step(withId: stepId) else { return }
        step.text = text
        currentStory = writeopiaManager.replaceStep(step, state: currentStory)
        changeCount += 1
    }

    public func removeStep(stepId: String) {
        aiAnswerFinished(stepId: stepId)
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

    /// Moves the step with `stepId` right after the step at `position`, from the reorder drag.
    public func moveStep(stepId: String, after position: Double) {
        _ = moveRequest(payload: dragPayload(for: StoryStep(id: stepId, type: .text, position: 0)), after: position)
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
