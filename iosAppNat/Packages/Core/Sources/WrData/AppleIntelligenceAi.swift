import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

/// Whether the on-device model of Apple Intelligence can answer, and why not when it can't.
public enum AppleIntelligenceStatus: Equatable, Sendable {
    case available
    /// The device doesn't support Apple Intelligence.
    case deviceNotEligible
    /// Apple Intelligence is turned off in the Settings app.
    case notEnabled
    /// The model is still downloading or getting ready.
    case modelNotReady
    /// The system is older than iOS 26.
    case unsupportedSystem
    case unavailable

    public var isAvailable: Bool { self == .available }

    public var message: String {
        switch self {
        case .available:
            String(localized: "Apple Intelligence is ready. AI runs on this device and your text never leaves it.")
        case .deviceNotEligible:
            String(localized: "This device doesn't support Apple Intelligence.")
        case .notEnabled:
            String(localized: "Turn on Apple Intelligence in the Settings app to use AI on this device.")
        case .modelNotReady:
            String(localized: "Apple Intelligence is getting ready. Try again in a few minutes.")
        case .unsupportedSystem:
            String(localized: "Apple Intelligence needs iOS 26 or macOS 26.")
        case .unavailable:
            String(localized: "Apple Intelligence isn't available right now.")
        }
    }
}

/// Runs the AI commands with the on-device model of Apple Intelligence (Foundation Models), so
/// AI works offline, in the private space, and without sending the text anywhere.
public final class AppleIntelligenceAi: AiStreaming {
    /// Characters sent to the model in one request. The on-device context holds about 4096
    /// tokens for the instructions, the prompt and the answer, so longer texts are condensed in
    /// parts first.
    let inputLimit: Int

    public init(inputLimit: Int = 6000) {
        self.inputLimit = inputLimit
    }

    public static var status: AppleIntelligenceStatus {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, macOS 26.0, *) {
            switch SystemLanguageModel.default.availability {
            case .available:
                return .available
            case .unavailable(.deviceNotEligible):
                return .deviceNotEligible
            case .unavailable(.appleIntelligenceNotEnabled):
                return .notEnabled
            case .unavailable(.modelNotReady):
                return .modelNotReady
            case .unavailable:
                return .unavailable
            }
        }
        #endif
        return .unsupportedSystem
    }

    public static var isAvailable: Bool { status.isAvailable }

    public func prewarm() {
        Self.prewarm()
    }

    /// Loads the model ahead of the first request, e.g. when the AI menu opens.
    public static func prewarm() {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, macOS 26.0, *), isAvailable {
            LanguageModelSession(instructions: AiPrompts.instructions).prewarm()
        }
        #endif
    }

    public func stream(_ command: AiCommand, prompt: String) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    try await self.run(command, text: prompt) { continuation.yield($0) }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: Self.mapped(error))
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// Tokens of the answer and of the text sent with a command. With the instructions they stay
    /// within the ~4096 tokens of the on-device context.
    static let answerTokens = 1200
    static let inputTokens = 2400
    static let noteTokens = 300

    private func run(_ command: AiCommand, text: String, onPartial: (String) -> Void) async throws {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, macOS 26.0, *) {
            let status = Self.status
            guard status.isAvailable else { throw AiStreamError(message: status.message) }

            // A free prompt carries the user's instruction, which condensing could drop: it's
            // sent as is, and the model reports when it's too long.
            let input = command == .prompt ? text : try await fitting(text)
            let session = LanguageModelSession(instructions: AiPrompts.instructions)
            let stream = session.streamResponse(
                to: AiPrompts.prompt(for: command, text: input),
                options: GenerationOptions(maximumResponseTokens: Self.answerTokens)
            )
            for try await snapshot in stream {
                try Task.checkCancellation()
                onPartial(snapshot.content)
            }
            return
        }
        #endif
        throw AiStreamError(message: AppleIntelligenceStatus.unsupportedSystem.message)
    }

    /// `text` when it fits in one request; otherwise its parts condensed one by one, until the
    /// notes fit. Throws when they still don't fit after a few rounds.
    private func fitting(_ text: String) async throws -> String {
        var text = text
        var partLimit = inputLimit
        // Each round shrinks the text a lot; the cap only guards against a model that doesn't.
        for _ in 0..<3 {
            if await fits(text) { return text }
            // Text with many tokens per character (e.g. Chinese) needs smaller parts.
            partLimit = min(partLimit, max(500, text.count / 2))

            var notes: [String] = []
            for part in AiPrompts.chunks(of: text, limit: partLimit) {
                try Task.checkCancellation()
                notes.append(try await condense(part))
            }
            text = notes.joined(separator: "\n")
        }
        if await fits(text) { return text }
        // Cutting the notes would silently drop the end of the text.
        throw AiStreamError(message: Self.tooLongMessage)
    }

    static var tooLongMessage: String {
        String(localized: "The text is too long for Apple Intelligence. Try with fewer lines.")
    }

    /// Whether `text` can be sent with a command. Counts the tokens when the system can (iOS
    /// 26.4), the characters otherwise.
    private func fits(_ text: String) async -> Bool {
        guard text.count <= inputLimit else { return false }
        #if canImport(FoundationModels)
        if #available(iOS 26.4, macOS 26.4, *),
           let tokens = try? await SystemLanguageModel.default.tokenCount(for: text) {
            return tokens <= Self.inputTokens
        }
        #endif
        return true
    }

    /// Short notes of `part`. A part the model can't take is split in two; as a last resort it's
    /// cut, so one bad part doesn't fail the whole command.
    private func condense(_ part: String, depth: Int = 0) async throws -> String {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, macOS 26.0, *) {
            do {
                let session = LanguageModelSession(instructions: AiPrompts.condenseInstructions)
                return try await session.respond(
                    to: AiPrompts.condensePrompt(for: part),
                    options: GenerationOptions(maximumResponseTokens: Self.noteTokens)
                ).content
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                guard depth < 2, part.count > 200 else { return String(part.prefix(inputLimit / 4)) }
                var notes: [String] = []
                for half in AiPrompts.chunks(of: part, limit: part.count / 2 + 1) {
                    notes.append(try await condense(half, depth: depth + 1))
                }
                return notes.joined(separator: "\n")
            }
        }
        #endif
        return part
    }

    /// Errors of the model as messages the editor can show in the answer.
    static func mapped(_ error: Error) -> Error {
        if error is CancellationError || error is AiStreamError { return error }

        #if canImport(FoundationModels)
        if #available(iOS 26.0, macOS 26.0, *), let error = error as? LanguageModelSession.GenerationError {
            let message: String = switch error {
            case .guardrailViolation, .refusal:
                String(localized: "Apple Intelligence can't answer about this text.")
            case .exceededContextWindowSize:
                tooLongMessage
            case .unsupportedLanguageOrLocale:
                String(localized: "Apple Intelligence doesn't support the language of this text yet.")
            case .assetsUnavailable:
                AppleIntelligenceStatus.modelNotReady.message
            case .rateLimited, .concurrentRequests:
                String(localized: "Apple Intelligence is busy. Try again in a moment.")
            default:
                error.localizedDescription
            }
            return AiStreamError(message: message)
        }
        #endif
        return AiStreamError(message: error.localizedDescription)
    }
}
