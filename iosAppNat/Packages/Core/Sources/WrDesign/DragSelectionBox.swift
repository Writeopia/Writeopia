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
    @ObservationIgnored private var frames: [String: CGRect] = [:]
    @ObservationIgnored private var inside: Set<String> = []

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
        rect = nil
        inside = []
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
                    Rectangle()
                        .fill(WrColors.accent.opacity(0.2))
                        .overlay(Rectangle().strokeBorder(WrColors.accent, lineWidth: 1))
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
