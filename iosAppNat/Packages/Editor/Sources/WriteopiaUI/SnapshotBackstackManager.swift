import Writeopia

/// Undo and redo history of the editor, as snapshots of the document taken before each edit.
/// Mirrors `SnapshotBackstackManager` of the Kotlin SDK: edits made by typing in the same line
/// are grouped, so one undo removes a few characters instead of one.
///
/// Steps are value types, so a snapshot shares the text of every line that didn't change with
/// the snapshots around it; the history is still capped at `limit` entries.
final class SnapshotBackstackManager {
    static let defaultTextEditLimit = 7
    static let defaultLimit = 200

    private let textEditLimit: Int
    private let limit: Int
    private var lastEditPosition: Double?
    private var textEditCount = 0

    private var backStates: [StoryState] = []
    private var forwardStates: [StoryState] = []

    init(textEditLimit: Int = SnapshotBackstackManager.defaultTextEditLimit, limit: Int = SnapshotBackstackManager.defaultLimit) {
        self.textEditLimit = textEditLimit
        self.limit = limit
    }

    var canUndo: Bool { !backStates.isEmpty }
    var canRedo: Bool { !forwardStates.isEmpty }

    /// Remembers `state`, the document right before an edit. A new edit discards what could be
    /// redone. States equal to the last one remembered are skipped.
    func addState(_ state: StoryState) {
        lastEditPosition = nil
        textEditCount = 0
        push(state)
    }

    /// Like `addState`, for text typed at `position`: the first edit in a line is remembered,
    /// then one in every `textEditLimit` edits of the same line, like the Kotlin SDK.
    func addTextState(_ state: StoryState, position: Double) {
        if position == lastEditPosition, textEditCount < textEditLimit {
            textEditCount += 1
            return
        }
        lastEditPosition = position
        textEditCount = 1
        push(state)
    }

    /// The state to go back to from `current`, which becomes the next state to redo. Nil when
    /// there is nothing to undo. Snapshots with the same content as `current` are skipped.
    func previousState(current: StoryState) -> StoryState? {
        while let state = backStates.popLast() {
            guard state.stories != current.stories else { continue }
            forwardStates.append(current)
            endTextEdition()
            return state
        }
        return nil
    }

    /// The state to go forward to from `current`, which becomes the next state to undo. Nil
    /// when there is nothing to redo.
    func nextState(current: StoryState) -> StoryState? {
        guard let state = forwardStates.popLast() else { return nil }
        backStates.append(current)
        endTextEdition()
        return state
    }

    /// Forgets the whole history, for a new document.
    func clear() {
        backStates.removeAll()
        forwardStates.removeAll()
        endTextEdition()
    }

    private func push(_ state: StoryState) {
        forwardStates.removeAll()
        guard backStates.last?.stories != state.stories else { return }
        backStates.append(state)
        if backStates.count > limit {
            backStates.removeFirst(backStates.count - limit)
        }
    }

    /// Whatever is typed next starts a new group, so it can be undone back to this point.
    private func endTextEdition() {
        lastEditPosition = nil
        textEditCount = 0
    }
}
