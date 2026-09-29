import SwiftUI

public extension EnvironmentValues {
    /// True while the window is wider than it is tall (landscape), like `isWideScreen` of the
    /// Compose app: the tab bar gives way to a side menu and the editor uses its side options.
    @Entry var isWideLayout: Bool = false
}

public extension View {
    /// Measures this view and sets `isWideLayout` for everything inside it.
    func readsWideLayout() -> some View {
        modifier(WideLayoutReader())
    }
}

private struct WideLayoutReader: ViewModifier {
    @State private var isWide = false

    func body(content: Content) -> some View {
        content
            .environment(\.isWideLayout, isWide)
            .background {
                // The whole window: the keyboard mustn't make a portrait iPad look wide.
                Color.clear
                    .ignoresSafeArea()
                    .onGeometryChange(for: Bool.self) { proxy in
                        proxy.size.width > proxy.size.height
                    } action: { isWide = $0 }
            }
    }
}

/// Counts the editors on screen, so the side menu of landscape can step aside and leave the
/// whole width to the text, like the Compose app does.
@MainActor
@Observable
public final class OpenEditors {
    public private(set) var count = 0

    public init() {}

    public var isAnyOpen: Bool { count > 0 }

    public func opened() {
        count += 1
    }

    public func closed() {
        count = max(0, count - 1)
    }
}

public extension EnvironmentValues {
    @Entry var openEditors: OpenEditors?
}
