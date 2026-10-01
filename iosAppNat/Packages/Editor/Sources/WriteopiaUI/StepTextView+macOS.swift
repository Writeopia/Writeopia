#if os(macOS)
import AppKit
import SwiftUI
import Writeopia
import WrModels

/// Editable text of a step on the Mac, with the formatting popup floating above its selection.
struct StepTextView: View {
    let step: StoryStep
    let manager: WriteopiaStateManager
    @State private var topInEditor: CGFloat = .infinity

    private var selectionRect: CGRect? {
        guard let selection = manager.textSelection, selection.stepId == step.id, !selection.isEmpty else { return nil }
        return manager.textSelectionRect
    }

    var body: some View {
        StepTextViewRepresentable(step: step, manager: manager)
            .background {
                // Near the top of the editor the popup goes under the selection instead.
                GeometryReader { geometry in
                    let top = geometry.frame(in: .named(ReorderCoordinator.coordinateSpace)).minY
                    Color.clear
                        .onAppear { topInEditor = top }
                        .onChange(of: top) { _, top in topInEditor = top }
                }
            }
            .overlay(alignment: .topLeading) {
                if let rect = selectionRect {
                    let spacing: CGFloat = 8
                    let above = rect.minY - TextSelectionToolbar.height - spacing
                    let fitsAbove = topInEditor + above >= 0
                    TextSelectionToolbar(manager: manager)
                        .fixedSize()
                        .offset(x: max(0, rect.minX), y: fitsAbove ? above : rect.maxY + spacing)
                        .transition(.opacity.combined(with: .scale(scale: 0.95)))
                        .zIndex(1)
                }
            }
            .animation(.easeOut(duration: 0.12), value: selectionRect == nil)
    }
}

/// An `NSTextView` without its scroll view, sized to its text, with the native spell check,
/// dictation and input methods. Return, Backspace at the start, and the arrows at the first or
/// last line go to the state manager, like the `UITextView` of iOS and the key handling of the
/// Compose desktop app.
struct StepTextViewRepresentable: NSViewRepresentable {
    let step: StoryStep
    let manager: WriteopiaStateManager

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeNSView(context: Context) -> StepNSTextView {
        let textView = StepNSTextView(usingTextLayoutManager: false)
        textView.delegate = context.coordinator
        textView.drawsBackground = false
        textView.textContainerInset = .zero
        textView.textContainer?.lineFragmentPadding = 0
        textView.textContainer?.widthTracksTextView = true
        textView.textContainer?.containerSize = NSSize(width: 320, height: CGFloat.greatestFiniteMagnitude)
        textView.isVerticallyResizable = true
        textView.isHorizontallyResizable = false
        textView.minSize = .zero
        textView.maxSize = NSSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)
        textView.isRichText = true
        textView.usesFontPanel = false
        textView.usesRuler = false
        textView.importsGraphics = false
        textView.allowsImageEditing = false
        // The document is the source of truth; its history will come from the model, not the view.
        textView.allowsUndo = false
        textView.isContinuousSpellCheckingEnabled = true
        textView.isAutomaticSpellingCorrectionEnabled = true
        textView.setAccessibilityIdentifier("step.\(step.type.name)")
        textView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        // Steps are reordered with a SwiftUI drag; the text view mustn't take dropped text.
        textView.unregisterDraggedTypes()

        textView.textStorage?.setAttributedString(TextStyles.attributedText(for: step, family: manager.fontFamily))
        textView.typingAttributes = TextStyles.baseAttributes(for: step, family: manager.fontFamily)
        context.coordinator.renderedStep = step
        context.coordinator.renderedFont = manager.fontFamily
        return textView
    }

    func updateNSView(_ textView: StepNSTextView, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self
        textView.isEditable = manager.isEditable
        textView.onFocusChange = { [weak manager, stepId = step.id] hasFocus in
            manager?.onFocusChange(stepId: stepId, hasFocus: hasFocus)
        }
        textView.onCopyLines = { [weak manager] in
            guard let manager, manager.hasSelectedLines else { return false }
            manager.onCopySelectedLines?()
            return true
        }
        textView.onCutLines = { [weak manager] in
            guard let manager, manager.hasSelectedLines else { return false }
            manager.onCutSelectedLines?()
            return true
        }

        render(step, in: textView, coordinator: coordinator)

        if let request = manager.focusRequest, request.stepId == step.id, coordinator.appliedRequest != request.id {
            coordinator.appliedRequest = request.id
            DispatchQueue.main.async { [weak manager] in
                manager?.focusRequestHandled(request)
                if textView.window?.firstResponder !== textView {
                    textView.window?.makeFirstResponder(textView)
                }
                let length = (textView.string as NSString).length
                textView.setSelectedRange(NSRange(location: min(request.cursor, length), length: 0))
            }
        }
    }

    /// SwiftUI probes several widths (including none at all) before settling on one; the height
    /// for each is measured off screen, so the text on screen always wraps at the frame width.
    func sizeThatFits(_ proposal: ProposedViewSize, nsView textView: StepNSTextView, context: Context) -> CGSize? {
        let width: CGFloat = if let proposed = proposal.width, proposed.isFinite, proposed > 0 {
            proposed
        } else if textView.bounds.width > 0 {
            textView.bounds.width
        } else {
            320
        }
        let minHeight = TextStyles.lineHeight(of: TextStyles.baseFont(for: step, family: manager.fontFamily))
        return CGSize(width: width, height: max(textView.height(forWidth: width), ceil(minHeight)))
    }

    /// Updates the text view from the model without disturbing what the user is typing.
    fileprivate func render(_ step: StoryStep, in textView: StepNSTextView, coordinator: Coordinator) {
        let text = step.text ?? ""
        coordinator.isRendering = true
        defer { coordinator.isRendering = false }

        guard coordinator.renderedStep != step || coordinator.renderedFont != manager.fontFamily || textView.string != text else {
            return
        }
        // Don't touch the text while an input method is composing.
        guard !textView.hasMarkedText() else { return }
        coordinator.renderedStep = step
        coordinator.renderedFont = manager.fontFamily

        if textView.string != text {
            let selection = textView.selectedRange()
            textView.textStorage?.setAttributedString(TextStyles.attributedText(for: step, family: manager.fontFamily))
            let length = (text as NSString).length
            textView.setSelectedRange(NSRange(location: min(selection.location, length), length: 0))
        } else if let storage = textView.textStorage {
            let fullRange = NSRange(location: 0, length: storage.length)
            storage.beginEditing()
            storage.setAttributes(TextStyles.baseAttributes(for: step, family: manager.fontFamily), range: fullRange)
            TextStyles.applySpans(of: step, to: storage, family: manager.fontFamily)
            storage.endEditing()
        }

        textView.typingAttributes = TextStyles.baseAttributes(for: step, family: manager.fontFamily)
        textView.invalidateIntrinsicContentSize()
    }

    final class Coordinator: NSObject, NSTextViewDelegate {
        var parent: StepTextViewRepresentable
        var renderedStep: StoryStep?
        var appliedRequest: UUID?
        var renderedFont: EditorFont = .system
        /// Set while the model is written into the view, so those selection changes aren't
        /// reported back as user selections during a SwiftUI update.
        var isRendering = false

        init(parent: StepTextViewRepresentable) {
            self.parent = parent
        }

        private var manager: WriteopiaStateManager { parent.manager }

        func textView(_ textView: NSTextView, doCommandBy selector: Selector) -> Bool {
            switch selector {
            case #selector(NSResponder.insertNewline(_:)):
                // Return splits the step; the model decides the new layout.
                let range = textView.selectedRange()
                let newText = (textView.string as NSString).replacingCharacters(in: range, with: "\n")
                manager.handleTextInput(newText, cursor: range.location + 1, stepId: parent.step.id)
                return true

            case #selector(NSResponder.deleteForward(_:)) where manager.hasSelectedLines:
                manager.deleteSelectedLines()
                return true

            case #selector(NSResponder.deleteBackward(_:)):
                if manager.hasSelectedLines {
                    manager.deleteSelectedLines()
                    return true
                }
                let range = textView.selectedRange()
                guard range.location == 0, range.length == 0 else { return false }
                manager.onErase(stepId: parent.step.id)
                return true

            case #selector(NSResponder.moveUp(_:)):
                guard lineEdges(of: textView).first else { return false }
                manager.focusPrevious(stepId: parent.step.id, cursor: textView.selectedRange().location)
                return true

            case #selector(NSResponder.moveDown(_:)):
                guard lineEdges(of: textView).last else { return false }
                manager.focusNext(stepId: parent.step.id, cursor: textView.selectedRange().location)
                return true

            case #selector(NSResponder.moveUpAndModifySelection(_:)) where manager.hasSelectedLines:
                manager.extendLineSelection(up: true)
                return true

            case #selector(NSResponder.moveDownAndModifySelection(_:)) where manager.hasSelectedLines:
                manager.extendLineSelection(up: false)
                return true

            case #selector(NSResponder.insertTab(_:)):
                // Tab accepts the AI suggestions, like the Compose app; it never indents.
                if manager.hasAiSuggestions {
                    manager.acceptAiSuggestions()
                }
                return true

            case #selector(NSResponder.cancelOperation(_:)):
                guard manager.hasSelectedLines else { return false }
                manager.clearLineSelection()
                return true

            default:
                return false
            }
        }

        func textView(_ textView: NSTextView, shouldChangeTextIn range: NSRange, replacementString text: String?) -> Bool {
            guard let text, text.contains("\n") else { return true }

            // Pasting several lines splits the step, like Return does.
            let newText = (textView.string as NSString).replacingCharacters(in: range, with: text)
            manager.handleTextInput(newText, cursor: range.location + (text as NSString).length, stepId: parent.step.id)
            return false
        }

        func textDidChange(_ notification: Notification) {
            guard let textView = notification.object as? StepNSTextView else { return }
            let text = textView.string
            manager.handleTextInput(text, cursor: textView.selectedRange().location, stepId: parent.step.id)

            // The model split the step if a "\n" slipped in; show this view's part right away.
            if text.contains("\n"), let step = manager.step(withId: parent.step.id) {
                parent.render(step, in: textView, coordinator: self)
            }
        }

        func textViewDidChangeSelection(_ notification: Notification) {
            guard !isRendering,
                  let textView = notification.object as? NSTextView,
                  textView.window?.firstResponder === textView,
                  !textView.hasMarkedText()
            else { return }
            let range = textView.selectedRange()
            var rect: CGRect?
            if range.length > 0, let layoutManager = textView.layoutManager, let container = textView.textContainer {
                let glyphs = layoutManager.glyphRange(forCharacterRange: range, actualCharacterRange: nil)
                rect = layoutManager.boundingRect(forGlyphRange: glyphs, in: container)
            }
            manager.onSelectionChange(stepId: parent.step.id, start: range.location, end: range.location + range.length, rect: rect)
        }

        /// Whether the cursor sits on the first and on the last line of the text.
        private func lineEdges(of textView: NSTextView) -> (first: Bool, last: Bool) {
            guard let layoutManager = textView.layoutManager, let container = textView.textContainer else { return (true, true) }
            let length = (textView.string as NSString).length
            guard length > 0 else { return (true, true) }

            layoutManager.ensureLayout(for: container)
            let location = textView.selectedRange().location
            let glyph = layoutManager.glyphIndexForCharacter(at: min(location, length - 1))
            var line = layoutManager.lineFragmentRect(forGlyphAt: glyph, effectiveRange: nil)
            if location >= length, let extra = Optional(layoutManager.extraLineFragmentRect), extra.height > 0 {
                line = extra
            }
            let used = layoutManager.usedRect(for: container)
            return (line.minY <= used.minY + 0.5, line.maxY >= used.maxY - 0.5)
        }
    }
}

/// Reports focus, lets the selected lines take Copy and Cut over the text, and measures its
/// height for any width without disturbing the text on screen.
final class StepNSTextView: NSTextView {
    var onFocusChange: ((Bool) -> Void)?
    /// Return true when the selected lines were copied, so the text isn't.
    var onCopyLines: (() -> Bool)?
    var onCutLines: (() -> Bool)?

    /// A second layout of the same text storage, used only to measure.
    private lazy var measuringContainer: NSTextContainer = {
        let container = NSTextContainer(size: NSSize(width: 320, height: CGFloat.greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        container.widthTracksTextView = false
        let layoutManager = NSLayoutManager()
        layoutManager.addTextContainer(container)
        textStorage?.addLayoutManager(layoutManager)
        return container
    }()

    /// Height of the text when wrapped at `width`.
    func height(forWidth width: CGFloat) -> CGFloat {
        let container = measuringContainer
        if container.containerSize.width != width {
            container.containerSize = NSSize(width: width, height: CGFloat.greatestFiniteMagnitude)
        }
        guard let layoutManager = container.layoutManager else { return 0 }
        layoutManager.ensureLayout(for: container)
        return ceil(layoutManager.usedRect(for: container).height)
    }

    override var intrinsicContentSize: NSSize {
        NSSize(width: NSView.noIntrinsicMetric, height: bounds.width > 0 ? height(forWidth: bounds.width) : NSView.noIntrinsicMetric)
    }

    override func setFrameSize(_ newSize: NSSize) {
        super.setFrameSize(newSize)
        // The text on screen wraps at the frame, whatever width was measured last.
        if let container = textContainer, container.containerSize.width != newSize.width {
            container.containerSize = NSSize(width: newSize.width, height: CGFloat.greatestFiniteMagnitude)
        }
    }

    override func didChangeText() {
        super.didChangeText()
        // A new or removed line changes the height; SwiftUI measures again.
        invalidateIntrinsicContentSize()
    }

    override func becomeFirstResponder() -> Bool {
        let accepted = super.becomeFirstResponder()
        if accepted { onFocusChange?(true) }
        return accepted
    }

    override func resignFirstResponder() -> Bool {
        let accepted = super.resignFirstResponder()
        if accepted { onFocusChange?(false) }
        return accepted
    }

    override func copy(_ sender: Any?) {
        if onCopyLines?() == true { return }
        super.copy(sender)
    }

    override func cut(_ sender: Any?) {
        if onCutLines?() == true { return }
        super.cut(sender)
    }
}
#endif
