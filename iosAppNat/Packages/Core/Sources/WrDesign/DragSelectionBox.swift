import Observation
import SwiftUI

/// Dragging on the empty space of a list draws a box and selects everything it touches, like
/// `DragSelectionBox` of the Compose desktop app. Items report their frames with
/// `selectableByDrag(id:)`; the container draws the box with `dragSelectionBox(_:)`.
@MainActor
@Observable
public final class DragSelection {
    public static let coordinateSpace = "wrDragSelection"

    /// The box being dragged, in the container's coordinate space; nil between drags.
    public private(set) var rect: CGRect?

    /// Called with an item's id when the box starts or stops touching it.
    @ObservationIgnored public var onChange: (_ id: String, _ isInside: Bool) -> Void = { _, _ in }
    /// The scroll view of the container: it scrolls while the pointer is near its top or bottom
    /// edge, so a box can grow past what's on screen.
    @ObservationIgnored public weak var scrollView: (any EditorScrolling)?
    @ObservationIgnored private var frames: [String: CGRect] = [:]
    @ObservationIgnored private var inside: Set<String> = []
    @ObservationIgnored private var start: CGPoint = .zero
    @ObservationIgnored private var current: CGPoint = .zero
    @ObservationIgnored private var autoScroll: Timer?

    public init() {}

    public var isActive: Bool { rect != nil }

    public func setFrame(_ frame: CGRect, for id: String) {
        frames[id] = frame
    }

    public func removeFrame(for id: String) {
        frames.removeValue(forKey: id)
        inside.remove(id)
    }

    /// The box from where the drag started to where the pointer is now.
    public func update(from start: CGPoint, to location: CGPoint) {
        if rect == nil {
            startAutoScroll()
        }
        self.start = start
        current = location
        let box = CGRect(
            x: min(start.x, location.x),
            y: min(start.y, location.y),
            width: abs(location.x - start.x),
            height: abs(location.y - start.y)
        )
        rect = box

        let touched = Set(frames.filter { $0.value.intersects(box) }.map(\.key))
        for id in touched.subtracting(inside) {
            onChange(id, true)
        }
        for id in inside.subtracting(touched) {
            onChange(id, false)
        }
        inside = touched
    }

    public func end() {
        autoScroll?.invalidate()
        autoScroll = nil
        rect = nil
        inside = []
    }

    // MARK: - Auto scroll

    private func startAutoScroll() {
        autoScroll?.invalidate()
        let timer = Timer(timeInterval: 1 / 60, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.autoScrollStep() }
        }
        // While the mouse drags, the run loop tracks the mouse: only the common modes get ticks.
        RunLoop.main.add(timer, forMode: .common)
        autoScroll = timer
    }

    /// Scrolls when the pointer is within `edge` of the top or bottom of the scroll view, and
    /// grows the box by the same amount since the pointer stays still on screen. One step of
    /// the timer; public so tests can drive it.
    public func autoScrollStep() {
        guard rect != nil, let scrollView else { return }

        let offset = scrollView.scrollOffset
        // Content coordinates minus the offset: where the pointer is in the visible area.
        let pointerInView = current.y - offset
        let visibleTop = scrollView.topInset
        let visibleBottom = scrollView.visibleHeight - scrollView.bottomInset
        let edge: CGFloat = 60

        var delta: CGFloat = 0
        if pointerInView < visibleTop + edge {
            delta = -max(2, (visibleTop + edge - pointerInView) / 6)
        } else if pointerInView > visibleBottom - edge {
            delta = max(2, (pointerInView - (visibleBottom - edge)) / 6)
        }
        guard delta != 0 else { return }

        let minOffset = -scrollView.topInset
        let maxOffset = max(minOffset, scrollView.contentHeight - scrollView.visibleHeight + scrollView.bottomInset)
        let newOffset = min(max(offset + delta, minOffset), maxOffset)
        guard newOffset != offset else { return }

        scrollView.scrollOffset = newOffset
        update(from: start, to: CGPoint(x: current.x, y: current.y + (newOffset - offset)))
    }
}

private struct DragSelectionKey: EnvironmentKey {
    static let defaultValue: DragSelection? = nil
}

public extension EnvironmentValues {
    var dragSelection: DragSelection? {
        get { self[DragSelectionKey.self] }
        set { self[DragSelectionKey.self] = newValue }
    }
}

public extension View {
    /// Reports the frame of an item to the drag selection of the container.
    func selectableByDrag(id: String) -> some View {
        modifier(DragSelectableItem(id: id))
    }

    /// Draws the selection box over this container and drives `selection` from a drag on it.
    /// A pointer interaction: it does nothing on iOS, where a drag scrolls.
    func dragSelectionBox(_ selection: DragSelection, enabled: Bool = true) -> some View {
        modifier(DragSelectionContainer(selection: selection, enabled: enabled))
    }
}

private struct DragSelectableItem: ViewModifier {
    let id: String
    @Environment(\.dragSelection) private var selection

    func body(content: Content) -> some View {
        content.background {
            GeometryReader { geometry in
                let frame = geometry.frame(in: .named(DragSelection.coordinateSpace))
                Color.clear
                    .onAppear { selection?.setFrame(frame, for: id) }
                    .onChange(of: frame) { _, frame in selection?.setFrame(frame, for: id) }
                    .onDisappear { selection?.removeFrame(for: id) }
            }
        }
    }
}

private struct DragSelectionContainer: ViewModifier {
    let selection: DragSelection
    let enabled: Bool

    func body(content: Content) -> some View {
        content
            .coordinateSpace(.named(DragSelection.coordinateSpace))
            .environment(\.dragSelection, selection)
            #if os(macOS)
            .gesture(dragGesture, including: enabled ? .all : .none)
            #endif
            .overlay {
                if let rect = selection.rect {
                    let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
                    shape
                        .fill(WrColors.accent.opacity(0.2))
                        .overlay(shape.strokeBorder(WrColors.accent, lineWidth: 1))
                        .frame(width: rect.width, height: rect.height)
                        .position(x: rect.midX, y: rect.midY)
                        .allowsHitTesting(false)
                }
            }
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 6, coordinateSpace: .named(DragSelection.coordinateSpace))
            .onChanged { value in selection.update(from: value.startLocation, to: value.location) }
            .onEnded { _ in selection.end() }
    }
}
