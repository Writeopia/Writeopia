import Foundation
import Observation
import WrModels

/// Where Claude Code meets the screens: the editors on screen say they are there, once their
/// document is loaded, so Claude can write into the one that shows a document; the app sets
/// `openDocument` to show the documents Claude asks for. Mirrors `ClaudeCodeBridge` of the
/// Compose app.
@MainActor
@Observable
public final class LiveEditors {
    public static let shared = LiveEditors()

    public struct Editor {
        public let documentId: String
        public let manager: WriteopiaStateManager
        /// Tags the document of this editor; nil where the editor has no tags.
        public let tagger: LiveTagger?
        /// What the editor is shown in (on the Mac, the tab's window): `bringToFront(owner:)`
        /// puts the editors of the tab the person moved to last, so they are `current`.
        public let owner: ObjectIdentifier?
    }

    /// The editors on screen, the one shown last at the end.
    public private(set) var editors: [Editor] = []

    /// Shows the document with this id and title in the editor. Set by the window of the app.
    public var openDocument: ((_ id: String, _ title: String) -> Void)?

    public init() {}

    /// The editor the person looks at now, if any.
    public var current: Editor? { editors.last }

    public func attach(
        documentId: String,
        manager: WriteopiaStateManager,
        tagger: LiveTagger? = nil,
        owner: AnyObject? = nil
    ) {
        editors.removeAll { $0.manager === manager }
        editors.append(
            Editor(
                documentId: documentId,
                manager: manager,
                tagger: tagger,
                owner: owner.map(ObjectIdentifier.init)
            )
        )
        manager.isInputLocked = mode.isOn
    }

    /// The person moved to the tab of `owner`: its editors go last, in their order, so the one
    /// it shows is `current`. The editors of the other tabs stay attached, out of sight.
    public func bringToFront(owner: AnyObject) {
        let id = ObjectIdentifier(owner)
        let front = editors.filter { $0.owner == id }
        guard !front.isEmpty else { return }
        editors = editors.filter { $0.owner != id } + front
    }

    public func detach(_ manager: WriteopiaStateManager) {
        editors.removeAll { $0.manager === manager }
        manager.isInputLocked = false
    }

    // MARK: - AI mode

    public enum AiMode: Equatable, Sendable {
        case off
        /// A call is typing into a document.
        case writing
        /// Between calls: Claude Code is preparing the next one.
        case waiting

        public var isOn: Bool { self != .off }
    }

    /// Whether Claude Code is at work: the app shows a banner with a button to stop it, the
    /// editors take no input and the writing agent stays quiet. On from the first call, it stays
    /// on between calls (Claude may take a while to write the next section) and ends
    /// `idleTimeout` after the last one, or when the person stops it. Mirrors
    /// `ClaudeCodeBridge.aiMode` of the Compose app.
    public private(set) var mode: AiMode = .off {
        didSet {
            guard mode.isOn != oldValue.isOn else { return }
            for editor in editors { editor.manager.isInputLocked = mode.isOn }
        }
    }

    /// How long AI mode outlives the last call.
    @ObservationIgnored public var idleTimeout: Duration = .seconds(60)
    /// How long calls are refused after the person stopped Claude Code.
    @ObservationIgnored public var stopCooldown: Duration = .seconds(60)

    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var stoppedGeneration = -1
    @ObservationIgnored private var stoppedAt: ContinuousClock.Instant?
    @ObservationIgnored private var idleTask: Task<Void, Never>?

    /// A call starts writing. Returns its ticket, or nil when the person stopped Claude Code a
    /// moment ago: the call is refused.
    public func beginWriting() -> Int? {
        if let stoppedAt, ContinuousClock.now - stoppedAt < stopCooldown { return nil }
        idleTask?.cancel()
        generation += 1
        mode = .writing
        return generation
    }

    /// The call with `ticket` is done: AI mode waits for the next one, then ends.
    public func endWriting(_ ticket: Int) {
        guard ticket == generation, !isStopped(ticket) else { return }
        mode = .waiting
        idleTask?.cancel()
        idleTask = Task { [weak self, idleTimeout] in
            try? await Task.sleep(for: idleTimeout)
            guard !Task.isCancelled else { return }
            self?.mode = .off
        }
    }

    /// Whether the person stopped the call with `ticket`.
    public func isStopped(_ ticket: Int) -> Bool { ticket <= stoppedGeneration }

    /// The person stops Claude Code: the call in progress stops where it is.
    public func stop() {
        idleTask?.cancel()
        stoppedGeneration = generation
        stoppedAt = .now
        mode = .off
    }

    /// Claude Code says it is done: AI mode ends now, without waiting for `idleTimeout`.
    public func finish() {
        idleTask?.cancel()
        mode = .off
    }

    /// Ends AI mode without refusing the next calls, like when the person turns the server off.
    public func reset() {
        idleTask?.cancel()
        stoppedGeneration = generation
        stoppedAt = nil
        mode = .off
    }

    /// The editor of `documentId` once it is on screen, or nil after `timeout`.
    public func awaitEditor(documentId: String, timeout: Duration = .seconds(15)) async -> Editor? {
        let deadline = ContinuousClock.now + timeout
        while ContinuousClock.now < deadline {
            if let editor = editors.last(where: { $0.documentId == documentId }) { return editor }
            try? await Task.sleep(for: .milliseconds(50))
        }
        return nil
    }
}

/// The tags of the document open in an editor, for Claude Code: the tags live with the document
/// (its view model), not in the text.
@MainActor
public protocol LiveTagger: AnyObject {
    var documentTagIds: [String] { get }
    func addTag(id: String)
    /// Makes a tag in the catalogue of the workspace and adds it to the document.
    @discardableResult
    func createTag(name: String, description: String) async throws -> DocumentTag
}
