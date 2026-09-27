#if canImport(UIKit)
import Observation
import SwiftUI
import Writeopia
import WrDesign
import WrModels

/// Horizontal swipe of a step, shared by the SwiftUI gesture of the row and the pan gesture of
/// its text view. Mirrors `SwipeBox` of the Kotlin SDK: the farther the step goes, the harder it
/// is to move, and a swipe longer than `threshold` toggles the selection of the step.
@Observable
final class SwipeTracker {
    static let maxDistance: CGFloat = 80
    static let threshold: CGFloat = 40

    private(set) var offset: CGFloat = 0
    private(set) var isDragging = false
    @ObservationIgnored private var lastTranslation: CGFloat = 0
    @ObservationIgnored var onSwipe: (() -> Void)?

    func changed(translation: CGFloat) {
        if !isDragging {
            isDragging = true
            lastTranslation = 0
        }
        let delta = translation - lastTranslation
        lastTranslation = translation

        let correction = max(0, (Self.maxDistance - abs(offset)) / Self.maxDistance)
        offset += delta * pow(correction, 3)
    }

    func ended() {
        guard isDragging else { return }
        isDragging = false
        lastTranslation = 0

        if abs(offset) > Self.threshold {
            onSwipe?()
        }
        withAnimation(.spring(response: 0.35, dampingFraction: 0.55)) {
            offset = 0
        }
    }

    func cancelled() {
        isDragging = false
        lastTranslation = 0
        withAnimation(.spring(response: 0.35, dampingFraction: 0.55)) {
            offset = 0
        }
    }
}

/// Recognizes horizontal pans over the steps and routes them to the swipe of the step under the
/// finger. A single UIKit recognizer on the editor's scroll view is used because SwiftUI drag
/// gestures inside a `ScrollView` can't be limited to one direction and block scrolling.
final class SwipeSelectionCoordinator: NSObject, UIGestureRecognizerDelegate {
    private final class WeakAnchor {
        weak var view: SwipeAnchorView?
        init(_ view: SwipeAnchorView) { self.view = view }
    }

    private var anchors: [ObjectIdentifier: WeakAnchor] = [:]
    private weak var scrollView: UIScrollView?
    private var pan: UIPanGestureRecognizer?
    private weak var activeTracker: SwipeTracker?

    func register(_ anchor: SwipeAnchorView) {
        anchors[ObjectIdentifier(anchor)] = WeakAnchor(anchor)
    }

    func unregister(_ anchor: SwipeAnchorView) {
        anchors.removeValue(forKey: ObjectIdentifier(anchor))
    }

    /// Adds the pan recognizer to the scroll view that contains `view`.
    func attach(toScrollViewOf view: UIView) {
        var current = view.superview
        while let candidate = current, !(candidate is UIScrollView) {
            current = candidate.superview
        }
        guard let scrollView = current as? UIScrollView, scrollView !== self.scrollView else { return }

        if let pan { self.scrollView?.removeGestureRecognizer(pan) }
        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        pan.delegate = self
        scrollView.addGestureRecognizer(pan)
        self.pan = pan
        self.scrollView = scrollView
    }

    @objc private func handlePan(_ pan: UIPanGestureRecognizer) {
        switch pan.state {
        case .began:
            activeTracker = anchor(at: pan.location(in: pan.view))?.tracker
        case .changed:
            activeTracker?.changed(translation: pan.translation(in: pan.view).x)
        case .ended:
            activeTracker?.ended()
            activeTracker = nil
        case .cancelled, .failed:
            activeTracker?.cancelled()
            activeTracker = nil
        default:
            break
        }
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let pan = gestureRecognizer as? UIPanGestureRecognizer, let scrollView = pan.view else { return false }

        // Only clearly horizontal drags; everything else is scrolling.
        let velocity = pan.velocity(in: scrollView)
        guard abs(velocity.x) > abs(velocity.y) * 1.5 else { return false }

        let location = pan.location(in: scrollView)
        // Leave drags on selected text alone, so the selection handles keep working.
        if let textView = scrollView.hitTest(location, with: nil) as? UITextView,
           textView.isFirstResponder, textView.selectedRange.length > 0 {
            return false
        }
        return anchor(at: location) != nil
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        otherGestureRecognizer === scrollView?.panGestureRecognizer
    }

    private func anchor(at location: CGPoint) -> SwipeAnchorView? {
        guard let scrollView else { return nil }
        return anchors.values.compactMap(\.view).first { anchor in
            anchor.window != nil && anchor.convert(anchor.bounds, to: scrollView).contains(location)
        }
    }
}

/// Invisible view behind a step that tells the coordinator where the step is on screen.
final class SwipeAnchorView: UIView {
    weak var coordinator: SwipeSelectionCoordinator?
    weak var tracker: SwipeTracker?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            coordinator?.register(self)
        } else {
            coordinator?.unregister(self)
        }
    }
}

private struct SwipeAnchor: UIViewRepresentable {
    let coordinator: SwipeSelectionCoordinator
    let tracker: SwipeTracker

    func makeUIView(context: Context) -> SwipeAnchorView {
        let view = SwipeAnchorView()
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        view.coordinator = coordinator
        view.tracker = tracker
        return view
    }

    func updateUIView(_ view: SwipeAnchorView, context: Context) {
        view.coordinator = coordinator
        view.tracker = tracker
        if view.window != nil { coordinator.register(view) }
    }
}

/// Placed once inside the editor's scroll view to attach the swipe recognizer to it.
struct SwipeSelectionInstaller: UIViewRepresentable {
    let coordinator: SwipeSelectionCoordinator

    func makeUIView(context: Context) -> InstallerView {
        let view = InstallerView()
        view.isUserInteractionEnabled = false
        view.coordinator = coordinator
        return view
    }

    func updateUIView(_ view: InstallerView, context: Context) {
        view.coordinator = coordinator
    }

    final class InstallerView: UIView {
        weak var coordinator: SwipeSelectionCoordinator?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if window != nil { coordinator?.attach(toScrollViewOf: self) }
        }
    }
}

private struct SwipeSelectionKey: EnvironmentKey {
    static let defaultValue: SwipeSelectionCoordinator? = nil
}

extension EnvironmentValues {
    var swipeSelection: SwipeSelectionCoordinator? {
        get { self[SwipeSelectionKey.self] }
        set { self[SwipeSelectionKey.self] = newValue }
    }
}

/// Slide a step sideways to select it; slide again to unselect. Several steps can be selected.
struct SwipeToSelect: ViewModifier {
    let step: StoryStep
    let manager: WriteopiaStateManager
    @Environment(\.swipeSelection) private var swipeSelection
    @State private var tracker = SwipeTracker()
    @State private var feedback = 0

    private var isSelected: Bool { manager.isSelected(stepId: step.id) }

    func body(content: Content) -> some View {
        content
            .padding(.vertical, 2)
            .background {
                if let swipeSelection {
                    SwipeAnchor(coordinator: swipeSelection, tracker: tracker)
                }
            }
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(isSelected ? WrColors.accent.opacity(0.12) : Color.clear)
                    .overlay {
                        RoundedRectangle(cornerRadius: 10)
                            .strokeBorder(isSelected ? WrColors.accent : Color.clear, lineWidth: 1)
                    }
                    .padding(.horizontal, -4)
            }
            .offset(x: tracker.offset)
            .sensoryFeedback(.selection, trigger: feedback)
            .animation(.easeInOut(duration: 0.2), value: isSelected)
            .accessibilityAddTraits(isSelected ? .isSelected : [])
            .accessibilityAction(named: isSelected ? "Unselect line" : "Select line") {
                manager.toggleLineSelection(stepId: step.id)
            }
            .onAppear {
                tracker.onSwipe = { [manager, stepId = step.id] in
                    manager.toggleLineSelection(stepId: stepId)
                    feedback += 1
                }
            }
    }
}

extension View {
    func swipeToSelect(_ step: StoryStep, manager: WriteopiaStateManager) -> some View {
        modifier(SwipeToSelect(step: step, manager: manager))
    }
}
#endif
