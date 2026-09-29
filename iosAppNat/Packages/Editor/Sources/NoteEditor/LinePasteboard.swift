import UniformTypeIdentifiers
import WriteopiaUI
import WrModels
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// Copies lines to the system pasteboard as plain text and as rich text, so the formatting
/// survives when they're pasted into apps like Notes or Mail.
struct SystemLinePasteboard: LineClipboard {
    func copy(_ lines: [StoryStep]) {
        let plain = lines.compactMap(\.text).joined(separator: "\n")

        let rich = NSMutableAttributedString()
        for (index, line) in lines.enumerated() {
            if index > 0 { rich.append(NSAttributedString(string: "\n")) }
            rich.append(StepText.attributedText(for: line))
        }
        let rtf = try? rich.data(
            from: NSRange(location: 0, length: rich.length),
            documentAttributes: [.documentType: NSAttributedString.DocumentType.rtf]
        )

        #if canImport(UIKit)
        var item: [String: Any] = [UTType.utf8PlainText.identifier: plain]
        if let rtf {
            item[UTType.rtf.identifier] = rtf
        }
        UIPasteboard.general.items = [item]
        #else
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(plain, forType: .string)
        if let rtf {
            pasteboard.setData(rtf, forType: .rtf)
        }
        #endif
    }
}
