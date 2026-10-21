import WrModels

/// Finds the steps that can receive the focus around a position, in one pass over the keys.
public struct FocusHandler {
    private let isFocusable: (StoryStep) -> Bool

    public init(isFocusable: @escaping (StoryStep) -> Bool = { $0.isTextStep }) {
        self.isFocusable = isFocusable
    }

    public func findNextFocus(after position: Int, in stories: [Int: StoryStep]) -> Int? {
        stories.reduce(nil) { best, entry in
            entry.key > position && isFocusable(entry.value) && (best == nil || entry.key < best!) ? entry.key : best
        }
    }

    public func findPreviousFocus(before position: Int, in stories: [Int: StoryStep]) -> Int? {
        stories.reduce(nil) { best, entry in
            entry.key < position && isFocusable(entry.value) && (best == nil || entry.key > best!) ? entry.key : best
        }
    }
}
