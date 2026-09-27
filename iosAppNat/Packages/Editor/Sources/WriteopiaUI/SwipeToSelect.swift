#if canImport(UIKit)
import SwiftUI
import Writeopia
import WrDesign
import WrModels

/// Slide a step sideways to select it; slide again to unselect. Several steps can be selected.
struct SwipeToSelect: ViewModifier {
    let step: StoryStep
    let manager: WriteopiaStateManager

    private var isSelected: Bool { manager.isSelected(stepId: step.id) }

    func body(content: Content) -> some View {
        content
            .padding(.vertical, 2)
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(isSelected ? WrColors.accent.opacity(0.12) : Color.clear)
                    .overlay {
                        RoundedRectangle(cornerRadius: 10)
                            .strokeBorder(isSelected ? WrColors.accent : Color.clear, lineWidth: 1)
                    }
                    .padding(.horizontal, -4)
            }
            .slideToSelect { [manager, stepId = step.id] in
                manager.toggleLineSelection(stepId: stepId)
            }
            .animation(.easeInOut(duration: 0.2), value: isSelected)
            .accessibilityAddTraits(isSelected ? .isSelected : [])
            .accessibilityAction(named: isSelected ? "Unselect line" : "Select line") {
                manager.toggleLineSelection(stepId: step.id)
            }
    }
}

extension View {
    func swipeToSelect(_ step: StoryStep, manager: WriteopiaStateManager) -> some View {
        modifier(SwipeToSelect(step: step, manager: manager))
    }
}
#endif
