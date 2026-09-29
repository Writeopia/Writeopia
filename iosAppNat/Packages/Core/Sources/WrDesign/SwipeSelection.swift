#if canImport(UIKit)
import Observation
import SwiftUI

/// Horizontal swipe of an item. Mirrors `SwipeBox` of the Kotlin SDK: the farther the item
/// goes, the harder it is to move, and a swipe longer than `threshold` toggles its selection.
@Observable
public final class SwipeTracker {
    static let maxDistance: CGFloat = 80
    static let threshold: CGFloat = 40

    public private(set) var offset: CGFloat = 0
    public private(set) var isDragging = false
    @ObservationIgnored private var lastTranslation: CGFloat = 0
    @ObservationIgnored public var onSwipe: (() -> Void)?

    public init() {}

    public func changed(translation: CGFloat) {
        if !isDragging {
            isDragging = true
            lastTranslation = 0
        }
        let delta = translation - lastTranslation
        lastTranslation = translation

        let correction = max(0, (Self.maxDistance - abs(offset)) / Self.maxDistance)
        offset += delta * pow(correction, 3)
    }

    public func ended() {
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

    public func cancelled() {
        isDragging = false
        lastTranslation = 0
        withAnimation(.spring(response: 0.35, dampingFraction: 0.55)) {
            offset = 0
        }
    }
}

/// Recognizes horizontal pans over the items of a scroll view and routes them to the swipe of the
/// item under the finger. A single UIKit recognizer on the editor's scroll view is used because SwiftUI drag
/// gestures inside a `ScrollView` can't be limited to one direction and block scrolling.
public final class SwipeSelectionCoordinator: NSObject, UIGestureRecognizerDelegate {
    private final class WeakAnchor {
        weak var view: SwipeAnchorView?
        init(_ view: SwipeAnchorView) { self.view = view }
    }

    private var anchors: [ObjectIdentifier: WeakAnchor] = [:]
    public private(set) weak var scrollView: UIScrollView?
    /// Called when the scroll view is found (the editor's reorder drag auto scrolls it).
    public var onScrollViewFound: ((UIScrollView) -> Void)?
    /// Width at the leading edge of each item where swipes don't start (e.g. a drag grip).
    private let leadingExclusion: CGFloat

    public init(leadingExclusion: CGFloat = 0) {
        self.leadingExclusion = leadingExclusion
    }
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
        onScrollViewFound?(scrollView)
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

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
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

    public func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        otherGestureRecognizer === scrollView?.panGestureRecognizer
    }

    private func anchor(at location: CGPoint) -> SwipeAnchorView? {
        guard let scrollView else { return nil }
        return anchors.values.compactMap(\.view).first { anchor in
            guard anchor.window != nil else { return false }
            let frame = anchor.convert(anchor.bounds, to: scrollView)
            return frame.contains(location) && location.x > frame.minX + leadingExclusion
        }
    }
}

/// Invisible view behind an item that tells the coordinator where the item is on screen.
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
public struct SwipeSelectionInstaller: UIViewRepresentable {
    let coordinator: SwipeSelectionCoordinator

    public init(coordinator: SwipeSelectionCoordinator) {
        self.coordinator = coordinator
    }

    public func makeUIView(context: Context) -> InstallerView {
        let view = InstallerView()
        view.isUserInteractionEnabled = false
        view.coordinator = coordinator
        return view
    }

    public func updateUIView(_ view: InstallerView, context: Context) {
        view.coordinator = coordinator
    }

    public final class InstallerView: UIView {
        weak var coordinator: SwipeSelectionCoordinator?

        override public func didMoveToWindow() {
            super.didMoveToWindow()
            if window != nil { coordinator?.attach(toScrollViewOf: self) }
        }
    }
}

private struct SwipeSelectionKey: EnvironmentKey {
    static let defaultValue: SwipeSelectionCoordinator? = nil
}

public extension EnvironmentValues {
    var swipeSelection: SwipeSelectionCoordinator? {
        get { self[SwipeSelectionKey.self] }
        set { self[SwipeSelectionKey.self] = newValue }
    }
}

/// Slides an item sideways to select it and back, with the elastic feel and haptic of the SDK.
/// The look of the selected state is left to the caller.
struct SlideToSelect: ViewModifier {
    let onSwipe: () -> Void
    @Environment(\.swipeSelection) private var swipeSelection
    @State private var tracker = SwipeTracker()
    @State private var feedback = 0

    func body(content: Content) -> some View {
        content
            .background {
                if let swipeSelection {
                    SwipeAnchor(coordinator: swipeSelection, tracker: tracker)
                }
            }
            .offset(x: tracker.offset)
            .sensoryFeedback(.selection, trigger: feedback)
            .onAppear {
                tracker.onSwipe = {
                    onSwipe()
                    feedback += 1
                }
            }
    }
}

public extension View {
    /// Slide sideways to toggle the selection; works inside a scroll view that has a
    /// `SwipeSelectionInstaller` and a `swipeSelection` coordinator in the environment.
    func slideToSelect(onSwipe: @escaping () -> Void) -> some View {
        modifier(SlideToSelect(onSwipe: onSwipe))
    }
}
#else
import SwiftUI

// The swipe gesture is a touch interaction; the Mac app selects with the pointer instead.
// These stand-ins keep the shared lists compiling and do nothing.

/// No-op on macOS; see the UIKit implementation above.
public final class SwipeSelectionCoordinator {
    public init(leadingExclusion: CGFloat = 0) {}
}

/// No-op on macOS; see the UIKit implementation above.
public struct SwipeSelectionInstaller: View {
    public init(coordinator: SwipeSelectionCoordinator) {}

    public var body: some View {
        EmptyView()
    }
}

private struct SwipeSelectionKey: EnvironmentKey {
    static let defaultValue: SwipeSelectionCoordinator? = nil
}

public extension EnvironmentValues {
    var swipeSelection: SwipeSelectionCoordinator? {
        get { self[SwipeSelectionKey.self] }
        set { self[SwipeSelectionKey.self] = newValue }
    }
}

public extension View {
    /// No-op on macOS: there is no swipe to select.
    func slideToSelect(onSwipe: @escaping () -> Void) -> some View {
        self
    }
}
#endif
