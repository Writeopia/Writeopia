import Foundation

/// The prompts of the AI commands, the same ones the backend sends to Gemini (`GenAiService`),
/// so Apple Intelligence answers like the cloud AI.
public enum AiPrompts {
    static let summary =
        "Summarize the following text while preserving its key points and main ideas. Use at most 12 lines. Keep the summary concise and clear. If the text contains multiple sections, highlight the most important aspects of each. Maintain the original tone and intent where possible. Detect the language of the text and write in the same language"

    static let actionPoints =
        "Extract key action points from the following text. Create just an introduction and the list of action items with at most 10. Don't add conclusions or introductions. Use the language of the text"

    static let faq =
        "Generate a list of frequently asked questions (FAQs) based on the following text. Include clear and concise answers that help users understand key points. Prioritize the most relevant and common concerns. Detect the language of the text and write in the same language"

    static let tags =
        "Generate a list of relevant tags based on the following text. The tags should capture key topics, themes, and important concepts. Use concise, single-word tags that accurately represent the content. Produce at most 10 tags. Detect the language of the text and write in the same language."

    /// Used to shrink a part of a long text so the whole of it fits in the on-device model.
    static let condense =
        "Rewrite the following part of a longer text as short notes that keep every fact, name, number, decision and task. Write in the same language as the text"

    /// Instructions of every on-device session. The editor shows the answer as Markdown.
    public static let instructions = """
        You are the writing assistant of Writeopia, a note taking app. \
        Answer with the content only, without greetings or comments about the task. \
        Format the answer as Markdown: use short paragraphs, "- " for lists and "## " for headings when useful.
        """

    /// Instructions of the sessions that condense a part of a long text: plain notes, so the
    /// final answer isn't shaped by the format of each part.
    static let condenseInstructions = """
        You condense texts for a note taking app. Answer with plain short notes, one per line, \
        without headings, greetings or comments. Don't repeat a note.
        """

    /// The whole prompt of `command` for `text`, in the format of the backend.
    public static func prompt(for command: AiCommand, text: String) -> String {
        guard let instruction = instruction(for: command) else { return text }
        return "\(instruction):\n```\n\(text)\n```"
    }

    static func instruction(for command: AiCommand) -> String? {
        switch command {
        case .prompt: nil
        case .summary: summary
        case .actionPoints: actionPoints
        case .faq: faq
        case .tags: tags
        }
    }

    static func condensePrompt(for text: String) -> String {
        "\(condense):\n```\n\(text)\n```"
    }

    /// Splits `text` in parts of at most `limit` characters, cutting between paragraphs, then
    /// lines, then words, so each part fits in the context of the on-device model.
    public static func chunks(of text: String, limit: Int) -> [String] {
        guard limit > 0, text.count > limit else { return [text] }

        var parts: [String] = []
        var current = ""

        func flush() {
            let trimmed = current.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { parts.append(trimmed) }
            current = ""
        }

        func append(_ piece: String, separator: String) {
            if current.isEmpty {
                current = piece
            } else if current.count + separator.count + piece.count <= limit {
                current += separator + piece
            } else {
                flush()
                current = piece
            }
        }

        for line in text.components(separatedBy: "\n") {
            if line.count <= limit {
                append(line, separator: "\n")
                continue
            }
            // A line too long for a part: cut it between words, or anywhere when a word is too long.
            for word in line.split(separator: " ", omittingEmptySubsequences: true).map(String.init) {
                var word = word
                while word.count > limit {
                    flush()
                    parts.append(String(word.prefix(limit)))
                    word = String(word.dropFirst(limit))
                }
                append(word, separator: " ")
            }
        }
        flush()
        return parts.isEmpty ? [String(text.prefix(limit))] : parts
    }
}
