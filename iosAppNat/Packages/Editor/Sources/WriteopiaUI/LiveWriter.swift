import Foundation
import Writeopia
import WrModels

/// Types blocks into an open editor, a few characters at a time, so the person sees the text
/// being written. Mirrors `LiveWriter.kt` of the Compose app.
///
/// While a block is typed the state changes without `changeCount`, so it isn't saved at every
/// character (like an AI answer streaming in); once whole, `changeCount` grows and the editor
/// saves and sends it like any edit.
///
/// Only adds: blocks go after the last one, and the trailing empty paragraph of the document, if
/// any, is filled instead of left behind (keeping its id). No step is ever removed, so no
/// tombstone is needed.
@MainActor
public struct LiveWriter {
    let tick: Duration
    /// The longest a call types for: a long text is typed faster, never slower than `minCharsPerTick`.
    let maxDuration: Duration
    let minCharsPerTick: Int

    public init(tick: Duration = .milliseconds(16), maxDuration: Duration = .seconds(8), minCharsPerTick: Int = 3) {
        self.tick = tick
        self.maxDuration = maxDuration
        self.minCharsPerTick = minCharsPerTick
    }

    /// Writes `title` into the title block (when given), then `blocks` at the end. Checks
    /// `isStopped` at every step: once true, the block being typed is saved as far as it got and
    /// nothing more is written. Returns true when everything was written, false when stopped.
    @discardableResult
    public func write(
        _ manager: WriteopiaStateManager,
        title: String?,
        blocks: [StoryStep],
        isStopped: () -> Bool = { false }
    ) async -> Bool {
        let total = (title?.utf16.count ?? 0) + blocks.filter(isTyped).reduce(0) { $0 + ($1.text?.utf16.count ?? 0) }
        let ticks = max(1, Int(maxDuration / tick))
        let perTick = max(minCharsPerTick, Int((Double(total) / Double(ticks)).rounded(.up)))

        if let title, var titleStep = manager.currentStory.sortedStories.first(where: \.isTitle) {
            titleStep.text = title
            titleStep.spans = []
            guard await type(titleStep, into: manager, perTick: perTick, isStopped: isStopped) else { return false }
        }

        var reusable = trailingEmptyParagraph(manager)

        for block in blocks {
            if isStopped() { return false }
            var step = block
            if let existing = reusable {
                step = block.withId(existing.id, position: existing.position)
            } else {
                let start = isTyped(block) ? block.partial(0) : block
                manager.currentStory = manager.writeopiaManager.addAtPosition(
                    start,
                    after: manager.lastPosition,
                    state: manager.currentStory
                )
                step.position = manager.step(withId: block.id)?.position ?? step.position
            }
            reusable = nil

            // Follow the writing down the document.
            manager.scroll(toStepId: step.id)

            if isTyped(step) {
                guard await type(step, into: manager, perTick: perTick, isStopped: isStopped) else { return false }
            } else {
                finish(step, in: manager)
            }
        }
        return true
    }

    /// Types `step`; false when it was stopped, with the text typed so far saved.
    private func type(
        _ step: StoryStep,
        into manager: WriteopiaStateManager,
        perTick: Int,
        isStopped: () -> Bool
    ) async -> Bool {
        let units = Array((step.text ?? "").utf16)
        var shown = 0

        while shown < units.count {
            if isStopped() {
                // What the person saw stays: the block as far as it got, saved.
                finish(step.partial(shown), in: manager)
                return false
            }
            shown = min(units.count, shown + perTick)
            // Never cut a character made of two UTF-16 units in half.
            if shown < units.count, UTF16.isLeadSurrogate(units[shown - 1]) { shown += 1 }
            guard manager.step(withId: step.id) != nil else { return true }
            manager.currentStory = manager.writeopiaManager.replaceStep(step.partial(shown), state: manager.currentStory)
            try? await Task.sleep(for: tick)
        }

        finish(step, in: manager)
        return true
    }

    /// The whole block, saved and sent by the editor.
    private func finish(_ step: StoryStep, in manager: WriteopiaStateManager) {
        guard manager.step(withId: step.id) != nil else { return }
        manager.currentStory = manager.writeopiaManager.replaceStep(step, state: manager.currentStory)
        manager.changeCount += 1
    }

    private func trailingEmptyParagraph(_ manager: WriteopiaStateManager) -> StoryStep? {
        guard let last = manager.currentStory.sortedStories.last else { return nil }
        let isEmptyParagraph = last.type == .text && (last.text ?? "").trimmingCharacters(in: .whitespaces).isEmpty
            && last.tags.isEmpty
        return isEmptyParagraph ? last : nil
    }

    private func isTyped(_ step: StoryStep) -> Bool {
        Self.typedTypes.contains(step.type) && !(step.text ?? "").isEmpty
    }

    private static let typedTypes: [StoryType] = [.title, .text, .unorderedListItem, .checkItem, .codeBlock]
}

extension StoryStep {
    /// The same block under another id, at `position`: it takes the place of the step with that id.
    func withId(_ id: String, position: String) -> StoryStep {
        StoryStep(
            id: id, type: type, text: text, checked: checked, url: url, path: path, steps: steps,
            tags: tags, spans: spans, position: position, documentLink: documentLink,
            parentId: parentId, decoration: decoration, lastUpdatedAt: lastUpdatedAt
        )
    }

    /// The step with its first `length` UTF-16 units, the spans cut to them.
    func partial(_ length: Int) -> StoryStep {
        let units = Array((text ?? "").utf16)
        let shown = min(length, units.count)
        var copy = self
        copy.text = String(decoding: units[0..<shown], as: UTF16.self)
        copy.spans = spans
            .filter { $0.start < shown }
            .map { SpanInfo(start: $0.start, end: min($0.end, shown), span: $0.span, extra: $0.extra) }
        return copy
    }
}
