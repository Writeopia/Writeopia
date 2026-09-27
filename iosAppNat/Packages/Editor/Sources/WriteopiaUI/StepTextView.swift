#if canImport(UIKit)
import SwiftUI
import UIKit
import Writeopia
import WrModels

/// Editable text of a step. A `UITextView` is used instead of a SwiftUI `TextField` because
/// the editor needs to show spans, split the step on Return and merge it on backspace at the
/// start, none of which `TextField` exposes.
struct StepTextView: UIViewRepresentable {
    let step: StoryStep
    let manager: WriteopiaStateManager

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> StepUITextView {
        let textView = StepUITextView()
        textView.delegate = context.coordinator
        textView.isScrollEnabled = false
        textView.backgroundColor = .clear
        textView.textContainerInset = .zero
        textView.textContainer.lineFragmentPadding = 0
        textView.adjustsFontForContentSizeCategory = true
        textView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        textView.accessibilityIdentifier = "step.\(step.type.name)"

        // Steps are reordered with SwiftUI drag and drop; the text view would swallow those drops.
        if let dropInteraction = textView.textDropInteraction {
            textView.removeInteraction(dropInteraction)
        }

        textView.attributedText = TextStyles.attributedText(for: step)
        textView.typingAttributes = TextStyles.baseAttributes(for: step)
        context.coordinator.renderedStep = step
        return textView
    }

    func updateUIView(_ textView: StepUITextView, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self
        textView.isEditable = manager.isEditable
        textView.onEraseAtStart = { [weak manager, stepId = step.id] in
            manager?.onErase(stepId: stepId)
        }

        render(step, in: textView, coordinator: coordinator)

        if let request = manager.focusRequest, request.stepId == step.id, coordinator.appliedRequest != request.id {
            coordinator.appliedRequest = request.id
            DispatchQueue.main.async { [weak manager] in
                manager?.focusRequestHandled(request)
                if !textView.isFirstResponder {
                    textView.becomeFirstResponder()
                }
                let length = (textView.text as NSString).length
                textView.selectedRange = NSRange(location: min(request.cursor, length), length: 0)
            }
        }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: StepUITextView, context: Context) -> CGSize? {
        let width = proposal.width ?? uiView.window?.bounds.width ?? 320
        let size = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: max(size.height, TextStyles.baseFont(for: step).lineHeight))
    }

    /// Updates the text view from the model without disturbing what the user is typing.
    private func render(_ step: StoryStep, in textView: StepUITextView, coordinator: Coordinator) {
        guard coordinator.renderedStep != step else { return }
        // Don't touch the text while the keyboard is composing (accents, CJK input...).
        guard textView.markedTextRange == nil else { return }
        coordinator.renderedStep = step

        let text = step.text ?? ""
        if textView.text != text {
            // The model changed the text (merge, split): replace it and keep the cursor in range.
            let selection = textView.selectedRange
            textView.attributedText = TextStyles.attributedText(for: step)
            let length = (text as NSString).length
            textView.selectedRange = NSRange(location: min(selection.location, length), length: 0)
        } else {
            // Same text: restyle in place, which keeps autocorrect and the selection intact.
            let storage = textView.textStorage
            let fullRange = NSRange(location: 0, length: storage.length)
            storage.beginEditing()
            storage.setAttributes(TextStyles.baseAttributes(for: step), range: fullRange)
            TextStyles.applySpans(of: step, to: storage)
            storage.endEditing()
        }

        textView.typingAttributes = TextStyles.baseAttributes(for: step)
        textView.invalidateIntrinsicContentSize()
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: StepTextView
        var renderedStep: StoryStep?
        var appliedRequest: UUID?

        init(parent: StepTextView) {
            self.parent = parent
        }

        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
            guard text.contains("\n") else { return true }

            // Return (or pasting several lines) splits the step. The model decides the new layout.
            let newText = (textView.text as NSString).replacingCharacters(in: range, with: text)
            let cursor = range.location + (text as NSString).length
            parent.manager.handleTextInput(newText, cursor: cursor, stepId: parent.step.id)
            return false
        }

        func textViewDidChange(_ textView: UITextView) {
            parent.manager.handleTextInput(textView.text, cursor: textView.selectedRange.location, stepId: parent.step.id)
        }

        func textViewDidBeginEditing(_ textView: UITextView) {
            parent.manager.onFocusChange(stepId: parent.step.id, hasFocus: true)
        }

        func textViewDidEndEditing(_ textView: UITextView) {
            parent.manager.onFocusChange(stepId: parent.step.id, hasFocus: false)
        }
    }
}

/// Reports backspace when the cursor is at the very start, which `UITextViewDelegate` doesn't.
final class StepUITextView: UITextView {
    var onEraseAtStart: (() -> Void)?

    override func deleteBackward() {
        if selectedRange.location == 0, selectedRange.length == 0 {
            onEraseAtStart?()
            return
        }
        super.deleteBackward()
    }
}
#endif
