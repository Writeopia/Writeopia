import WrModels

/// Writes the steps of a document as Markdown. Mirrors `DocumentToMarkdown` of the Kotlin SDK:
/// the title and headings become `#` lines, lists `- ` and checklists `[] `, and steps without
/// text (dividers...) are left out.
public enum DocumentToMarkdown {
    public static func parse(_ steps: [StoryStep]) -> String {
        steps
            .compactMap(line)
            .map { $0 + "\n" }
            .joined()
    }

    /// `label` with the characters that would end or break a link label escaped.
    private static func linkLabel(_ label: String) -> String {
        label
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "[", with: "\\[")
            .replacingOccurrences(of: "]", with: "\\]")
    }

    /// `url` as a link destination: as is when plain, inside angle brackets when it has spaces
    /// or parentheses, which would end the link early.
    private static func linkDestination(_ url: String) -> String {
        guard url.contains(where: { $0.isWhitespace || $0 == "(" || $0 == ")" }) else { return url }
        let escaped = url
            .replacingOccurrences(of: "<", with: "\\<")
            .replacingOccurrences(of: ">", with: "\\>")
        return "<\(escaped)>"
    }

    private static func line(_ step: StoryStep) -> String? {
        switch step.type.number {
        case StoryType.title.number:
            return "# \(step.text ?? "")"
        case StoryType.text.number:
            return textWithTags(step)
        case StoryType.checkItem.number:
            return textWithTags(step).map { "[] \($0)" }
        case StoryType.unorderedListItem.number:
            return textWithTags(step).map { "- \($0)" }
        case StoryType.drawing.number:
            // The text of a drawing is its JSON, not something to read.
            return nil
        case StoryType.linkCard.number:
            let url = step.url ?? ""
            let label = (step.text ?? "").isEmpty ? url : step.text!
            return "[\(linkLabel(label))](\(linkDestination(url)))"
        default:
            return step.text
        }
    }

    private static func textWithTags(_ step: StoryStep) -> String? {
        guard let text = step.text else { return nil }
        switch step.headingLevel {
        case 1: return "# \(text)"
        case 2: return "## \(text)"
        case 3: return "### \(text)"
        case 4: return "#### \(text)"
        default: return text
        }
    }
}
