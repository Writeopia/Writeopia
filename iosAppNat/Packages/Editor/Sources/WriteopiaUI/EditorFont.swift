import SwiftUI
#if canImport(UIKit)
import UIKit
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

    #if canImport(UIKit)
    func uiFont(size: CGFloat, weight: UIFont.Weight) -> UIFont {
        let system = UIFont.systemFont(ofSize: size, weight: weight)
        switch self {
        case .system:
            return system
        case .serif:
            return system.fontDescriptor.withDesign(.serif).map { UIFont(descriptor: $0, size: size) } ?? system
        case .monospace:
            return .monospacedSystemFont(ofSize: size, weight: weight)
        case .cursive:
            let name = weight >= .semibold ? "SnellRoundhand-Bold" : Self.cursiveName
            return UIFont(name: name, size: size) ?? system
        }
    }
    #endif
}
