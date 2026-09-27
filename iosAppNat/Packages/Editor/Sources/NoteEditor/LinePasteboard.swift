#if canImport(UIKit)
import UIKit
import UniformTypeIdentifiers
import WriteopiaUI
import WrModels

/// Copies lines to the system pasteboard as plain text and as rich text, so the formatting
/// survives when they're pasted into apps like Notes or Mail.
struct SystemLinePasteboard: LineClipboard {
    func copy(_ lines: [StoryStep]) {
        let plain = lines.compactMap(\.text).joined(separator: "\n")
        var item: [String: Any] = [UTType.utf8PlainText.identifier: plain]

        let rich = NSMutableAttributedString()
        for (index, line) in lines.enumerated() {
            if index > 0 { rich.append(NSAttributedString(string: "\n")) }
            rich.append(StepText.attributedText(for: line))
        }
        if let rtf = try? rich.data(
            from: NSRange(location: 0, length: rich.length),
            documentAttributes: [.documentType: NSAttributedString.DocumentType.rtf]
        ) {
            item[UTType.rtf.identifier] = rtf
        }

        UIPasteboard.general.items = [item]
    }
}
#endif
