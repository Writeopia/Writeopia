import SwiftUI
import WrDesign
import Writeopia
import WrModels

/// The editor: every step of the document drawn in order, editable in place.
public struct WriteopiaEditor: View {
    /// How the steps are laid out.
    public enum Layout {
        /// A document: the text starts at the top, in a column of at most 760 points, with the
        /// gutter of the drag grips and room to click below the last line.
        case document
        /// A slide of a presentation: the text is centered on the screen, the column is as
        /// wide as its longest line (up to 760 points) and there's no gutter.
        case slide
    }

    private let manager: WriteopiaStateManager
    private let customDrawers: [Int: CustomStepDrawer]
    private let layout: Layout
    // Swipes don't start on the grip column, which belongs to the reorder drag.
    @State private var swipeSelection = SwipeSelectionCoordinator(leadingExclusion: EditorLayout.gutter + 4)
    @State private var reorder = ReorderCoordinator()
    /// A drag on the empty space selects the lines it crosses, like the Compose desktop app.
    @State private var dragSelection = DragSelection()

    /// Room on the sides of the text. On the Mac it keeps the text clear of the column of side
    /// options that floats over the trailing edge.
    private static var horizontalPadding: CGFloat {
        #if os(macOS)
        64
        #else
        12
        #endif
    }

    /// `customDrawers` draws step types the editor doesn't know, by type number.
    public init(
        manager: WriteopiaStateManager,
        customDrawers: [Int: CustomStepDrawer] = [:],
        layout: Layout = .document
    ) {
        self.manager = manager
        self.customDrawers = customDrawers
        self.layout = layout
    }

    private static let maxColumnWidth: CGFloat = 760
    /// Padding below a slide: its center rises by half of it.
    private static let slideLift: CGFloat = 120

    /// The column of a slide: as wide as its longest line, so the block of text sits in the
    /// middle of the screen. Longer lines wrap at the width of a document.
    private var columnWidth: CGFloat {
        guard layout == .slide else { return Self.maxColumnWidth }
        let widths = manager.documentContent.map { step in
            Self.lineWidth(of: TextStyles.attributedText(for: step, family: manager.fontFamily)) + Self.decorationWidth(of: step)
        }
        // Some slack: the text views wrap when the frame is short by a single point.
        return min(Self.maxColumnWidth, ceil((widths.max() ?? 0) + 40))
    }

    /// The width of `text` on one line, laid out as the text views lay it out.
    private static func lineWidth(of text: NSAttributedString) -> CGFloat {
        let unbounded = CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)
        let rect = text.boundingRect(with: unbounded, options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        return ceil(rect.width)
    }

    /// What the drawers add beside the text of a step: the paddings of the title, a checkbox
    /// or a bullet.
    private static func decorationWidth(of step: StoryStep) -> CGFloat {
        switch step.type.number {
        case StoryType.title.number: 16
        case StoryType.checkItem.number: 36
        case StoryType.unorderedListItem.number: 24
        default: 0
        }
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
                    .padding(.top, 8)
                    .background { SwipeSelectionInstaller(coordinator: swipeSelection) }
                    .environment(\.reorderCoordinator, reorder)
                    .environment(\.swipeSelection, swipeSelection)
                    .environment(\.customStepDrawers, customDrawers)
                    .environment(\.isSlideLayout, layout == .slide)
                    .frame(maxWidth: columnWidth)
                    // Outside the text column: the text keeps its width, the padding keeps it
                    // clear of the window edges and the side menu.
                    .padding(.horizontal, Self.horizontalPadding)
                    .frame(maxWidth: .infinity)
                    // A slide keeps its own height, or a step offered the whole height would
                    // grow and sit at the top anyway.
                    .fixedSize(horizontal: false, vertical: layout == .slide)
                    // A slide sits a little above the middle, where the eye expects it, and
                    // clear of the arrows at the bottom.
                    .padding(.bottom, layout == .slide ? Self.slideLift : 0)
                    // The content fills the visible height, so a click on the empty space below
                    // the last step reaches the background too.
                    .frame(minHeight: geometry.size.height, alignment: layout == .slide ? .center : .top)
                    .contentShape(Rectangle())
                    .onTapGesture { manager.onBackgroundClick?() }
                    .dragSelectionBox(dragSelection, enabled: manager.isEditable)
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .onAppear {
                dragSelection.onChange = { [manager] id, isInside in
                    manager.onSelected(stepId: id, isSelected: isInside)
                }
                reorder.manager = manager
                swipeSelection.onScrollViewFound = { [reorder, dragSelection] scrollView in
                    reorder.scrollView = scrollView
                    dragSelection.scrollView = scrollView
                }
                if let scrollView = swipeSelection.scrollView {
                    reorder.scrollView = scrollView
                    dragSelection.scrollView = scrollView
                }
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
