#if canImport(UIKit)
import SwiftUI
import Writeopia
import WrModels

/// The editor: every step of the document drawn in order, editable in place.
public struct WriteopiaEditor: View {
    private let manager: WriteopiaStateManager
    @State private var swipeSelection = SwipeSelectionCoordinator()

    public init(manager: WriteopiaStateManager) {
        self.manager = manager
    }

    public var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(manager.toDraw) { draw in
                        StoryStepDrawer(draw: draw, manager: manager)
                            .id(draw.id)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.top, 8)
                .background { SwipeSelectionInstaller(coordinator: swipeSelection) }
                .environment(\.swipeSelection, swipeSelection)
                .frame(maxWidth: 760)
                .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: manager.focusRequest) { _, request in
                guard let request else { return }
                withAnimation(.easeOut(duration: 0.2)) {
                    proxy.scrollTo(request.stepId)
                }
            }
        }
    }
}
#endif
