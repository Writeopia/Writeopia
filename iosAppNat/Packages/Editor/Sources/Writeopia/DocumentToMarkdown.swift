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
