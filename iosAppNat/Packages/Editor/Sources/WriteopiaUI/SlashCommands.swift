import Foundation
import Writeopia
import WrModels

/// A command of the popup that opens when "/" is typed at the start of a word, like
/// `SlashCommand` of the Compose SDK: `/today` and `/now` insert text, the others change the
/// line the way the side menu does.
public struct SlashCommand: Identifiable {
    public enum Action {
        /// Replaces the typed command with the text it makes.
        case insertText(() -> String)
        /// Changes the line to `type`, like `onTypeChange` of the Compose popup.
        case changeType(StoryType)
        /// Toggles `tag` on the line, like `onTagToggle` of the Compose popup.
        case toggleTag(BlockTag)
    }

    public var id: String { commandText }
    public let name: String
    public let description: String
    public let systemImage: String
    /// The text that triggers the command, with its slash (e.g. "/today").
    public let commandText: String
    public let action: Action

    public init(name: String, description: String, systemImage: String, commandText: String, action: Action) {
        self.name = name
        self.description = description
        self.systemImage = systemImage
        self.commandText = commandText
        self.action = action
    }

    /// Whether the command is offered for what was typed after the "/", like the filter of the
    /// Compose popup: the name or the command text contains it, whatever the case.
    public func matches(_ filter: String) -> Bool {
        filter.isEmpty ||
            name.localizedCaseInsensitiveContains(filter) ||
            commandText.localizedCaseInsensitiveContains(filter)
    }

    /// The commands of the Compose apps, in their order.
    public static let defaults: [SlashCommand] = [
        SlashCommand(
            name: String(localized: "Today"),
            description: String(localized: "Insert today's date"),
            systemImage: "calendar",
            commandText: "/today",
            action: .insertText { SlashCommandDates.today() }
        ),
        SlashCommand(
            name: String(localized: "Now"),
            description: String(localized: "Insert current date and time"),
            systemImage: "clock",
            commandText: "/now",
            action: .insertText { SlashCommandDates.now() }
        ),
        SlashCommand(
            name: String(localized: "Checkbox"),
            description: String(localized: "Change line to a checkbox"),
            systemImage: "checkmark.square",
            commandText: "/checkbox",
            action: .changeType(.checkItem)
        ),
        SlashCommand(
            name: String(localized: "List"),
            description: String(localized: "Change line to a bullet list"),
            systemImage: "list.bullet",
            commandText: "/list",
            action: .changeType(.unorderedListItem)
        ),
        SlashCommand(
            name: String(localized: "Box"),
            description: String(localized: "Change line to a highlight box"),
            systemImage: "highlighter",
            commandText: "/box",
            action: .toggleTag(.box)
        ),
        SlashCommand(
            name: String(localized: "Card"),
            description: String(localized: "Change line to a card with borders"),
            systemImage: "square",
            commandText: "/card",
            action: .toggleTag(.card)
        ),
        SlashCommand(
            name: String(localized: "Divider"),
            description: String(localized: "Change line to a divider"),
            systemImage: "minus",
            commandText: "/divider",
            action: .changeType(.divider)
        ),
    ]
}

/// The dates `/today` and `/now` insert, formatted like `DateTimeUtils` of the Compose SDK: the
/// short date of the locale, and the time as `HH:mm`.
enum SlashCommandDates {
    static func today(_ date: Date = .now) -> String {
        date.formatted(date: .numeric, time: .omitted)
    }

    static func now(_ date: Date = .now) -> String {
        let time = DateFormatter()
        // A fixed format: without the POSIX locale the 12-hour setting of the device rewrites
        // "HH:mm" with an AM/PM marker.
        time.locale = Locale(identifier: "en_US_POSIX")
        time.dateFormat = "HH:mm"
        return "\(today(date)) \(time.string(from: date))"
    }
}

/// A "/" command being typed in a step: where its slash is and what was typed after it, which
/// filters the commands of the popup.
public struct SlashCommandInput: Equatable {
    public let stepId: String
    /// UTF-16 offset of the "/" in the text of the step.
    public let slashIndex: Int
    /// The text between the "/" and the cursor.
    public let filter: String

    /// The characters the command takes in the text: the "/" and the filter.
    public var range: NSRange { NSRange(location: slashIndex, length: 1 + filter.utf16.count) }
}

/// Follows the "/" command through the edits of a step, like the slash detection of
/// `TextDrawer` in the Compose SDK: a "/" typed at the start of the text or after a space opens
/// it, the letters typed after the slash filter the commands, a space closes it, and so does
/// deleting the slash. Offsets are UTF-16, like the text views.
enum SlashCommandDetector {
    static func update(
        _ current: SlashCommandInput?,
        stepId: String,
        oldText: String,
        newText: String,
        cursor: Int
    ) -> SlashCommandInput? {
        let old = oldText as NSString
        let new = newText as NSString
        guard cursor >= 0, cursor <= new.length else { return nil }
        // Typing in another step ends the command of the previous one.
        let current = current?.stepId == stepId ? current : nil
        let difference = new.length - old.length

        if difference > 0 {
            if character(of: new, at: cursor - 1) == "/" {
                let before = character(of: new, at: cursor - 2)
                if before == nil || before == " " || before == "\n" {
                    return SlashCommandInput(stepId: stepId, slashIndex: cursor - 1, filter: "")
                }
            }
            return current.flatMap { filtered($0, in: new, cursor: cursor) }
        } else if difference < 0 {
            guard let current, cursor > current.slashIndex else { return nil }
            return filtered(current, in: new, cursor: cursor)
        } else {
            // Same length (a replaced character, an autocorrection): the command stays while
            // its slash does.
            return current.flatMap { filtered($0, in: new, cursor: cursor) }
        }
    }

    private static func filtered(_ input: SlashCommandInput, in text: NSString, cursor: Int) -> SlashCommandInput? {
        let start = input.slashIndex + 1
        guard character(of: text, at: input.slashIndex) == "/", start <= cursor, cursor <= text.length else { return nil }
        let filter = text.substring(with: NSRange(location: start, length: cursor - start))
        guard !filter.contains(" "), !filter.contains("\n") else { return nil }
        return SlashCommandInput(stepId: input.stepId, slashIndex: input.slashIndex, filter: filter)
    }

    private static func character(of text: NSString, at index: Int) -> String? {
        guard index >= 0, index < text.length else { return nil }
        return text.substring(with: NSRange(location: index, length: 1))
    }
}
