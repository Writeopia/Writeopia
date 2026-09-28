import WrModels

/// Finds the steps that can receive the focus around a position.
public struct FocusHandler {
    private let isFocusable: (StoryStep) -> Bool

    public init(isFocusable: @escaping (StoryStep) -> Bool = { $0.isTextStep }) {
        self.isFocusable = isFocusable
    }

    public func findNextFocus(after position: Double, in stories: [Double: StoryStep]) -> Double? {
        stories.keys.sorted().first { $0 > position && stories[$0].map(isFocusable) == true }
    }

    public func findPreviousFocus(before position: Double, in stories: [Double: StoryStep]) -> Double? {
        stories.keys.sorted().last { $0 < position && stories[$0].map(isFocusable) == true }
    }
}
