import WrModels

/// An action performed in the text editor. Mirrors `Action` of the Kotlin SDK.
public enum Action {
    /// New content for the step at `position`, with the cursor after the edit.
    public struct StoryStateChange: Equatable {
        public let storyStep: StoryStep
        public let position: Double
        public let selectionStart: Int?
        public let selectionEnd: Int?

        public init(storyStep: StoryStep, position: Double, selectionStart: Int? = nil, selectionEnd: Int? = nil) {
            self.storyStep = storyStep
            self.position = position
            self.selectionStart = selectionStart
            self.selectionEnd = selectionEnd
        }
    }

    /// The text of `storyStep` contains one or more line breaks and must be split into steps.
    /// `cursor` is the cursor position in that text, used to decide where the focus goes.
    public struct LineBreak: Equatable {
        public let storyStep: StoryStep
        public let position: Double
        public let cursor: Int

        public init(storyStep: StoryStep, position: Double, cursor: Int) {
            self.storyStep = storyStep
            self.position = position
            self.cursor = cursor
        }
    }

    /// Backspace at the start of a step: its text joins the previous step.
    public struct EraseStory: Equatable {
        public let storyStep: StoryStep
        public let position: Double

        public init(storyStep: StoryStep, position: Double) {
            self.storyStep = storyStep
            self.position = position
        }
    }

    public struct DeleteStory: Equatable {
        public let position: Double

        public init(position: Double) {
            self.position = position
        }
    }

    /// Moves the step at `positionFrom` to right after the step at `positionTo`.
    public struct Move: Equatable {
        public let positionFrom: Double
        public let positionTo: Double

        public init(positionFrom: Double, positionTo: Double) {
            self.positionFrom = positionFrom
            self.positionTo = positionTo
        }
    }
}
