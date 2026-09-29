import SwiftUI
import WrDesign
import Writeopia
import WrModels

/// The editor: every step of the document drawn in order, editable in place.
public struct WriteopiaEditor: View {
    private let manager: WriteopiaStateManager
    private let customDrawers: [Int: CustomStepDrawer]
    // Swipes don't start on the grip column, which belongs to the reorder drag.
    @State private var swipeSelection = SwipeSelectionCoordinator(leadingExclusion: EditorLayout.gutter + 4)
    @State private var reorder = ReorderCoordinator()

    /// Room on the sides of the text. On the Mac it keeps the text clear of the column of side
    /// options that floats over the trailing edge.
    private static var horizontalPadding: CGFloat {
        #if os(macOS)
        56
        #else
        12
        #endif
    }

    /// `customDrawers` draws step types the editor doesn't know, by type number.
    public init(manager: WriteopiaStateManager, customDrawers: [Int: CustomStepDrawer] = [:]) {
        self.manager = manager
        self.customDrawers = customDrawers
    }

    /// The dragged step, floating under the finger.
    @ViewBuilder
    private var reorderPreview: some View {
        if let drag = reorder.active, let step = manager.step(withId: drag.stepId) {
            DragPreview(step: step)
                .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
                .offset(x: EditorLayout.gutter + 8, y: drag.location.y - 22)
                .allowsHitTesting(false)
                .transition(.opacity)
        }
    }

    public var body: some View {
        ScrollViewReader { proxy in
            GeometryReader { geometry in
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(manager.toDraw) { draw in
                            StoryStepDrawer(draw: draw, manager: manager)
                                .id(draw.id)
                        }
                    }
                    .coordinateSpace(.named(ReorderCoordinator.coordinateSpace))
                    .overlay(alignment: .topLeading) { reorderPreview }
                    .padding(.horizontal, Self.horizontalPadding)
                    .padding(.top, 8)
                    .background { SwipeSelectionInstaller(coordinator: swipeSelection) }
                    .environment(\.reorderCoordinator, reorder)
                    .environment(\.swipeSelection, swipeSelection)
                    .environment(\.customStepDrawers, customDrawers)
                    .frame(maxWidth: 760)
                    .frame(maxWidth: .infinity)
                    // The content fills the visible height, so a click on the empty space below
                    // the last step reaches the background too.
                    .frame(minHeight: geometry.size.height, alignment: .top)
                    .contentShape(Rectangle())
                    .onTapGesture { manager.onBackgroundClick?() }
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .onAppear {
                reorder.manager = manager
                swipeSelection.onScrollViewFound = { [reorder] scrollView in reorder.scrollView = scrollView }
                if let scrollView = swipeSelection.scrollView { reorder.scrollView = scrollView }
            }
            .onChange(of: manager.focusRequest) { _, request in
                guard let request else { return }
                withAnimation(.easeOut(duration: 0.2)) {
                    proxy.scrollTo(request.stepId)
                }
            }
        }
    }
}
