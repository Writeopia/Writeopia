#if canImport(UIKit)
import UIKit
import Writeopia
import WrModels

/// Fonts and attributes of each step, following `TextStyle.kt` of the Kotlin SDK.
enum TextStyles {
    static func baseFont(for step: StoryStep, family: EditorFont = .system) -> UIFont {
        if step.isTitle {
            return scaled(family.uiFont(size: 34, weight: .bold), style: .largeTitle)
        }

        switch step.headingLevel {
        case 1: return scaled(family.uiFont(size: 32, weight: .bold), style: .title1)
        case 2: return scaled(family.uiFont(size: 28, weight: .bold), style: .title2)
        case 3: return scaled(family.uiFont(size: 24, weight: .bold), style: .title3)
        case 4: return scaled(family.uiFont(size: 20, weight: .semibold), style: .headline)
        default: return scaled(family.uiFont(size: 17, weight: .regular), style: .body)
        }
    }

    static func baseAttributes(for step: StoryStep, family: EditorFont = .system) -> [NSAttributedString.Key: Any] {
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineSpacing = 2

        var attributes: [NSAttributedString.Key: Any] = [
            .font: baseFont(for: step, family: family),
            .foregroundColor: UIColor.label,
            .paragraphStyle: paragraph,
        ]

        if step.type.number == StoryType.checkItem.number, step.checked == true {
            attributes[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
            attributes[.foregroundColor] = UIColor.secondaryLabel
        }

        return attributes
    }

    /// Text of the step with its spans applied. Spans can't be created in the editor, but the
    /// ones already in the document are shown.
    static func attributedText(for step: StoryStep, family: EditorFont = .system) -> NSAttributedString {
        let text = step.text ?? ""
        let result = NSMutableAttributedString(string: text, attributes: baseAttributes(for: step, family: family))
        applySpans(of: step, to: result, family: family)
        return result
    }

    static func applySpans(of step: StoryStep, to text: NSMutableAttributedString, family: EditorFont = .system) {
        let length = text.length
        let base = baseFont(for: step, family: family)

        for span in step.spans {
            let start = max(0, min(span.start, length))
            let end = max(start, min(span.end, length))
            guard start < end else { continue }
            let range = NSRange(location: start, length: end - start)

            switch span.span {
            case "BOLD":
                addTraits(.traitBold, in: range, of: text, base: base)
            case "ITALIC":
                addTraits(.traitItalic, in: range, of: text, base: base)
            case "UNDERLINE":
                text.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: range)
            case "HIGHLIGHT":
                text.addAttribute(.backgroundColor, value: UIColor.systemYellow.withAlphaComponent(0.35), range: range)
            case "HIGHLIGHT_GREEN":
                text.addAttribute(.backgroundColor, value: UIColor.systemGreen.withAlphaComponent(0.3), range: range)
            case "HIGHLIGHT_RED":
                text.addAttribute(.backgroundColor, value: UIColor.systemRed.withAlphaComponent(0.3), range: range)
            case "LINK":
                text.addAttribute(.foregroundColor, value: UIColor.tintColor, range: range)
                text.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: range)
            default:
                break
            }
        }
    }

    private static func addTraits(
        _ trait: UIFontDescriptor.SymbolicTraits,
        in range: NSRange,
        of text: NSMutableAttributedString,
        base: UIFont
    ) {
        text.enumerateAttribute(.font, in: range) { value, subrange, _ in
            let font = value as? UIFont ?? base
            let traits = font.fontDescriptor.symbolicTraits.union(trait)
            if let descriptor = font.fontDescriptor.withSymbolicTraits(traits) {
                text.addAttribute(.font, value: UIFont(descriptor: descriptor, size: font.pointSize), range: subrange)
            }
        }
    }

    private static func scaled(_ font: UIFont, style: UIFont.TextStyle) -> UIFont {
        UIFontMetrics(forTextStyle: style).scaledFont(for: font)
    }
}
#endif
