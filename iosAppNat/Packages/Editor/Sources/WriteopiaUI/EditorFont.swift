import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// Font family of the editor. Mirrors `Font` of the Compose app.
public enum EditorFont: String, CaseIterable, Identifiable, Sendable {
    case system = "System"
    case serif = "Serif"
    case monospace = "Monospace"
    case cursive = "Cursive"

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .system: String(localized: "System")
        case .serif: String(localized: "Serif")
        case .monospace: String(localized: "Monospace")
        case .cursive: String(localized: "Cursive")
        }
    }

    /// SwiftUI font of this family, used for previews of the option.
    public func font(size: CGFloat, weight: Font.Weight = .regular) -> Font {
        switch self {
        case .system: .system(size: size, weight: weight)
        case .serif: .system(size: size, weight: weight, design: .serif)
        case .monospace: .system(size: size, weight: weight, design: .monospaced)
        case .cursive: .custom(Self.cursiveName, size: size)
        }
    }

    static let cursiveName = "SnellRoundhand"

    /// The text view font of this family (`UIFont` on iOS, `NSFont` on macOS).
    func platformFont(size: CGFloat, weight: PlatformFont.Weight) -> PlatformFont {
        let system = PlatformFont.systemFont(ofSize: size, weight: weight)
        switch self {
        case .system:
            return system
        case .serif:
            #if canImport(UIKit)
            return system.fontDescriptor.withDesign(.serif).map { UIFont(descriptor: $0, size: size) } ?? system
            #else
            return system.fontDescriptor.withDesign(.serif).flatMap { NSFont(descriptor: $0, size: size) } ?? system
            #endif
        case .monospace:
            return .monospacedSystemFont(ofSize: size, weight: weight)
        case .cursive:
            let name = weight >= .semibold ? "SnellRoundhand-Bold" : Self.cursiveName
            return PlatformFont(name: name, size: size) ?? system
        }
    }
}
