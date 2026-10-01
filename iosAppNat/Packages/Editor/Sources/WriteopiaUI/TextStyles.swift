#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif
import Writeopia
import WrModels

/// Fonts and attributes of each step, following `TextStyle.kt` of the Kotlin SDK. The same
/// code drives `UITextView` and `NSTextView`; only the font scaling differs (Dynamic Type has
/// no counterpart on the Mac).
enum TextStyles {
    private enum Scale {
        case largeTitle, title1, title2, title3, headline, body
    }

    static func baseFont(for step: StoryStep, family: EditorFont = .system) -> PlatformFont {
        if step.isTitle {
            return scaled(family.platformFont(size: 34, weight: .bold), style: .largeTitle)
        }
        if step.type.number == StoryType.codeBlock.number {
            return scaled(.monospacedSystemFont(ofSize: 15, weight: .regular), style: .body)
        }

        switch step.headingLevel {
        case 1: return scaled(family.platformFont(size: 32, weight: .bold), style: .title1)
        case 2: return scaled(family.platformFont(size: 28, weight: .bold), style: .title2)
        case 3: return scaled(family.platformFont(size: 24, weight: .bold), style: .title3)
        case 4: return scaled(family.platformFont(size: 20, weight: .semibold), style: .headline)
        default: return scaled(family.platformFont(size: 17, weight: .regular), style: .body)
        }
    }

    /// Height of one line of `font`, the least a step can measure.
    static func lineHeight(of font: PlatformFont) -> CGFloat {
        #if canImport(UIKit)
        font.lineHeight
        #else
        NSLayoutManager().defaultLineHeight(for: font)
        #endif
    }

    static func baseAttributes(for step: StoryStep, family: EditorFont = .system) -> [NSAttributedString.Key: Any] {
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineSpacing = 2

        var attributes: [NSAttributedString.Key: Any] = [
            .font: baseFont(for: step, family: family),
            .foregroundColor: labelColor,
            .paragraphStyle: paragraph,
        ]

        if step.type.number == StoryType.checkItem.number, step.checked == true {
            attributes[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
            attributes[.foregroundColor] = secondaryLabelColor
        }

        return attributes
    }

    /// Text of the step with its spans applied.
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
                addTraits(boldTrait, in: range, of: text, base: base)
            case "ITALIC":
                addTraits(italicTrait, in: range, of: text, base: base)
            case "UNDERLINE":
                text.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: range)
            case "HIGHLIGHT":
                text.addAttribute(.backgroundColor, value: PlatformColor.systemYellow.withAlphaComponent(0.35), range: range)
            case "HIGHLIGHT_GREEN":
                text.addAttribute(.backgroundColor, value: PlatformColor.systemGreen.withAlphaComponent(0.3), range: range)
            case "HIGHLIGHT_RED":
                text.addAttribute(.backgroundColor, value: PlatformColor.systemRed.withAlphaComponent(0.3), range: range)
            case "LINK":
                text.addAttribute(.foregroundColor, value: accentColor, range: range)
                text.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: range)
            default:
                break
            }
        }
    }

    // MARK: - Platform

    private static var labelColor: PlatformColor {
        #if canImport(UIKit)
        .label
        #else
        .labelColor
        #endif
    }

    private static var secondaryLabelColor: PlatformColor {
        #if canImport(UIKit)
        .secondaryLabel
        #else
        .secondaryLabelColor
        #endif
    }

    private static var accentColor: PlatformColor {
        #if canImport(UIKit)
        .tintColor
        #else
        .controlAccentColor
        #endif
    }

    private static var boldTrait: PlatformFontDescriptor.SymbolicTraits {
        #if canImport(UIKit)
        .traitBold
        #else
        .bold
        #endif
    }

    private static var italicTrait: PlatformFontDescriptor.SymbolicTraits {
        #if canImport(UIKit)
        .traitItalic
        #else
        .italic
        #endif
    }

    private static func addTraits(
        _ trait: PlatformFontDescriptor.SymbolicTraits,
        in range: NSRange,
        of text: NSMutableAttributedString,
        base: PlatformFont
    ) {
        text.enumerateAttribute(.font, in: range) { value, subrange, _ in
            let font = value as? PlatformFont ?? base
            let traits = font.fontDescriptor.symbolicTraits.union(trait)
            #if canImport(UIKit)
            if let descriptor = font.fontDescriptor.withSymbolicTraits(traits) {
                text.addAttribute(.font, value: UIFont(descriptor: descriptor, size: font.pointSize), range: subrange)
            }
            #else
            let descriptor = font.fontDescriptor.withSymbolicTraits(traits)
            if let styled = NSFont(descriptor: descriptor, size: font.pointSize) {
                text.addAttribute(.font, value: styled, range: subrange)
            }
            #endif
        }
    }

    private static func scaled(_ font: PlatformFont, style: Scale) -> PlatformFont {
        #if canImport(UIKit)
        let textStyle: UIFont.TextStyle = switch style {
        case .largeTitle: .largeTitle
        case .title1: .title1
        case .title2: .title2
        case .title3: .title3
        case .headline: .headline
        case .body: .body
        }
        return UIFontMetrics(forTextStyle: textStyle).scaledFont(for: font)
        #else
        return font
        #endif
    }
}

/// Styled text of a step, for places outside the editor (e.g. the clipboard).
public enum StepText {
    public static func attributedText(for step: StoryStep, font: EditorFont = .system) -> NSAttributedString {
        TextStyles.attributedText(for: step, family: font)
    }
}
