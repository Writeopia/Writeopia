import Foundation
import Writeopia
import WrModels

/// Markdown-like commands typed at the start of a line followed by a space, like
/// `TextCommandHandler` and `CommandFactory` of the Compose app: `[] ` makes a checkbox, `- ` a
/// list item, `# `…`#### ` headings, ```` ``` ```` a code block, `--- ` a divider, `/box ` and
/// `/card ` toggle the blocks.
enum TextCommands {
    enum Command: String, CaseIterable {
        case checkItem = "-[]"
        case checkItem2 = "[]"
        case box = "/box"
        case card = "/card"
        case list = "-"
        case h1 = "#"
        case h2 = "##"
        case h3 = "###"
        case h4 = "####"
        case codeBlock = "```"
        case divider = "---"
    }

    /// The command `text` starts with, followed by a space (e.g. `### Heading`, `--- `), with
    /// the text after the space. The space is what triggers it: `#` alone must not become a
    /// heading, or `##` and `###` could never be typed.
    static func command(in text: String) -> (command: Command, rest: String)? {
        guard let space = text.firstIndex(of: " "), let command = Command(rawValue: String(text[..<space])) else {
            return nil
        }
        return (command, String(text[text.index(after: space)...]))
    }
}
