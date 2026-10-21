import Foundation
import Writeopia
import WrModels

/// What the writing agent needs to apply its suggestions: inserting blocks around a step found
/// by its id, since the document may have changed while the AI answered.
public extension WriteopiaStateManager {
    /// Adds `steps`, in order, right below the step with `stepId`, or at the end when it's gone.
    func addSteps(_ steps: [StoryStep], afterStepId stepId: String?) {
        guard isEditable, !steps.isEmpty else { return }
        let target = stepId.flatMap { id in self.position(of: id) } ?? lastPosition
        if let target {
            currentStory = writeopiaManager.addSteps(steps, after: target, state: currentStory)
        } else {
            for step in steps {
                currentStory = writeopiaManager.addAtPosition(step, after: lastPosition, state: currentStory)
            }
        }
        currentStory.focus = nil
        changeCount += 1
    }

    /// Adds `step` right above the step with `stepId`, like a heading above its paragraph.
    func addStep(_ step: StoryStep, beforeStepId stepId: String) {
        guard isEditable, let target = self.position(of: stepId) else { return }
        currentStory = writeopiaManager.addStep(step, before: target, state: currentStory)
        currentStory.focus = nil
        changeCount += 1
    }

    /// Adds `step` right below the title, at the top of the body.
    func addStepAfterTitle(_ step: StoryStep) {
        guard isEditable else { return }
        let title = currentStory.sortedStories.first(where: \.isTitle)
        addSteps([step], afterStepId: title?.id)
    }

    /// Puts `step` in place of the step with the same id, like the picture that replaces its
    /// loading block, or the AI answer that grows while it streams.
    func replaceStep(_ step: StoryStep) {
        guard self.step(withId: step.id) != nil else { return }
        currentStory = writeopiaManager.replaceStep(step, state: currentStory)
        changeCount += 1
    }

    /// Adds or removes `tag` on the step with `stepId`, like the callout of the writing agent.
    func toggleTag(_ tag: BlockTag, stepId: String) {
        guard isEditable, let target = self.position(of: stepId) else { return }
        let newState = writeopiaManager.toggleTag(tag, positions: [target], state: currentStory)
        guard newState != currentStory else { return }
        currentStory = newState
        changeCount += 1
    }

    /// Writes `title` into the title step, the one shown in the header.
    func setTitle(_ title: String) {
        guard isEditable, var step = currentStory.sortedStories.first(where: \.isTitle) else { return }
        step.text = title
        currentStory = writeopiaManager.replaceStep(step, state: currentStory)
        changeCount += 1
    }

    /// Adds a link to the document `documentId` right below the step with `stepId`.
    func addDocumentLink(afterStepId stepId: String?, documentId: String, title: String) {
        guard isEditable else { return }
        guard let target = stepId.flatMap({ id in self.position(of: id) }) ?? lastPosition else { return }
        currentStory = writeopiaManager.addDocumentLink(after: target, documentId: documentId, title: title, state: currentStory)
        changeCount += 1
    }

    /// The step with the cursor, if it's a text step: what the writing agent watches.
    var focusedStep: StoryStep? {
        guard let focus = currentStory.focus, let step = currentStory.stories[focus], step.isTextStep else { return nil }
        return step
    }

    /// The text of the whole document, title included, one step per line.
    var documentTextWithTitle: String {
        currentStory.sortedStories
            .filter { $0.isTextStep && !$0.isAiSuggestion }
            .compactMap(\.text)
            .filter { !$0.isEmpty }
            .joined(separator: "\n")
    }
}
