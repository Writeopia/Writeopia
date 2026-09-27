#if canImport(UIKit)
import Observation
import SwiftUI
import UIKit
import Writeopia
import WrModels

/// Drag to reorder steps, started from the grip of a step as soon as the finger moves (no
/// long press, unlike system drag and drop). Mirrors the drag of the Kotlin SDK: over the upper
/// half of a step the dragged step goes above it, over the lower half below it, and the
/// `ON_DRAG_SPACE` shows where it will land.
@Observable
final class ReorderCoordinator {
    static let coordinateSpace = "writeopiaEditor"

    struct ActiveDrag: Equatable {
        let stepId: String
        /// Finger position in the editor content.
        var location: CGPoint
    }

    private(set) var active: ActiveDrag?

    @ObservationIgnored weak var manager: WriteopiaStateManager?
    @ObservationIgnored weak var scrollView: UIScrollView?
    @ObservationIgnored private var frames: [String: CGRect] = [:]
    @ObservationIgnored private var scrollOffsetAtLastEvent: CGFloat = 0
    @ObservationIgnored private var autoScroll: Timer?

    /// Steps report where they are, in the editor content.
    func setFrame(_ frame: CGRect, for stepId: String) {
        frames[stepId] = frame
    }

    func removeFrame(for stepId: String) {
        frames.removeValue(forKey: stepId)
    }

    // MARK: - Gesture

    func dragChanged(stepId: String, location: CGPoint) {
        if active == nil {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            startAutoScroll()
        }
        active = ActiveDrag(stepId: stepId, location: location)
        scrollOffsetAtLastEvent = scrollView?.contentOffset.y ?? 0
        updateTarget()
    }

    func dragEnded() {
        defer { finish() }
        guard let active, let manager, let target = dropPosition(for: active) else { return }
        withAnimation(.snappy) {
            manager.moveStep(stepId: active.stepId, after: target)
        }
    }

    func dragCancelled() {
        finish()
    }

    private func finish() {
        autoScroll?.invalidate()
        autoScroll = nil
        active = nil
        manager?.onDragStop()
    }

    // MARK: - Target

    private func updateTarget() {
        guard let active else { return }
        manager?.onDragHover(dropPosition(for: active))
    }

    /// Position after which the dragged step would land, or nil when dropping it there would
    /// leave it where it is.
    func dropPosition(for drag: ActiveDrag) -> Double? {
        guard let manager else { return nil }
        let state = manager.currentStory
        let positions = state.sortedPositions
        guard let from = positions.first(where: { state.stories[$0]?.id == drag.stepId }) else { return nil }

        let rows = positions.compactMap { position -> (position: Double, step: StoryStep, frame: CGRect)? in
            guard let step = state.stories[position], let frame = frames[step.id] else { return nil }
            return (position, step, frame)
        }
        guard let lastRow = rows.last else { return nil }

        let y = drag.location.y
        let target: Double
        if let row = rows.first(where: { y < $0.frame.maxY }) {
            if y >= row.frame.midY || row.step.isTitle {
                // Lower half (or the title, nothing goes above it): below this step.
                target = row.position
            } else {
                // Upper half: below the step before it.
                let index = positions.firstIndex(of: row.position) ?? 0
                target = index > 0 ? positions[index - 1] : row.position
            }
        } else {
            target = lastRow.position
        }

        // Right after itself or after the step above it: nothing would move.
        let fromIndex = positions.firstIndex(of: from) ?? 0
        if target == from || (fromIndex > 0 && target == positions[fromIndex - 1]) {
            return nil
        }
        return target
    }

    // MARK: - Auto scroll

    /// Scrolls while the finger is close to the top or bottom edge, like `AutoScrollLazyColumn`.
    private func startAutoScroll() {
        autoScroll?.invalidate()
        autoScroll = Timer.scheduledTimer(withTimeInterval: 1 / 60, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.autoScrollTick() }
        }
    }

    private func autoScrollTick() {
        guard var drag = active, let scrollView else { return }

        let offset = scrollView.contentOffset.y
        let insets = scrollView.adjustedContentInset
        // Where the finger is on screen, from its last position in the content.
        let fingerInView = drag.location.y + (scrollOffsetAtLastEvent - offset) - offset
        let visibleTop = insets.top
        let visibleBottom = scrollView.bounds.height - insets.bottom
        let edge: CGFloat = 70

        var delta: CGFloat = 0
        if fingerInView < visibleTop + edge {
            delta = -max(2, (visibleTop + edge - fingerInView) / 6)
        } else if fingerInView > visibleBottom - edge {
            delta = max(2, (fingerInView - (visibleBottom - edge)) / 6)
        }
        guard delta != 0 else { return }

        let minOffset = -insets.top
        let maxOffset = max(minOffset, scrollView.contentSize.height - scrollView.bounds.height + insets.bottom)
        let newOffset = min(max(offset + delta, minOffset), maxOffset)
        guard newOffset != offset else { return }

        scrollView.contentOffset.y = newOffset
        // The finger stays still on screen while the content moves under it.
        drag.location.y += newOffset - offset
        scrollOffsetAtLastEvent = newOffset
        active = drag
        updateTarget()
    }
}

private struct ReorderCoordinatorKey: EnvironmentKey {
    static let defaultValue: ReorderCoordinator? = nil
}

extension EnvironmentValues {
    var reorderCoordinator: ReorderCoordinator? {
        get { self[ReorderCoordinatorKey.self] }
        set { self[ReorderCoordinatorKey.self] = newValue }
    }
}

extension View {
    /// Reports the frame of a step to the reorder coordinator.
    func reorderFrame(stepId: String) -> some View {
        modifier(ReorderFrameReporter(stepId: stepId))
    }
}

private struct ReorderFrameReporter: ViewModifier {
    let stepId: String
    @Environment(\.reorderCoordinator) private var coordinator

    func body(content: Content) -> some View {
        content.background {
            GeometryReader { geometry in
                let frame = geometry.frame(in: .named(ReorderCoordinator.coordinateSpace))
                Color.clear
                    .onAppear { coordinator?.setFrame(frame, for: stepId) }
                    .onChange(of: frame) { _, frame in coordinator?.setFrame(frame, for: stepId) }
                    .onDisappear { coordinator?.removeFrame(for: stepId) }
            }
        }
    }
}

/// The grip of a step: dragging it moves the step, starting right away.
struct ReorderGrip: View {
    let step: StoryStep
    @Environment(\.reorderCoordinator) private var coordinator

    var body: some View {
        Image(systemName: "line.3.horizontal")
            .font(.caption)
            .foregroundStyle(.tertiary)
            .frame(width: EditorLayout.gutter, height: 24)
            .contentShape(Rectangle().inset(by: -8))
            .gesture(
                DragGesture(minimumDistance: 2, coordinateSpace: .named(ReorderCoordinator.coordinateSpace))
                    .onChanged { value in
                        coordinator?.dragChanged(stepId: step.id, location: value.location)
                    }
                    .onEnded { _ in coordinator?.dragEnded() }
            )
            .accessibilityLabel("Reorder")
            .accessibilityIdentifier("drag.\(step.id)")
    }
}
#endif
