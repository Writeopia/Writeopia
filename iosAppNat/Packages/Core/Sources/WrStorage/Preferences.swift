import Foundation

/// Small typed wrapper around `UserDefaults` for values that are not secrets.
public final class Preferences {
    public enum Key: String, CaseIterable {
        case spaceType = "wr.spaceType"
        case selectedWorkspace = "wr.selectedWorkspace"
        case currentUser = "wr.currentUser"
        case colorTheme = "wr.colorTheme"
        case pendingEmail = "wr.pendingEmail"
        case passwordResetEmail = "wr.passwordResetEmail"
        case documentsArrangement = "wr.documentsArrangement"
        case documentsOrder = "wr.documentsOrder"
    }

    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public func string(_ key: Key) -> String? {
        defaults.string(forKey: key.rawValue)
    }

    public func set(_ value: String?, for key: Key) {
        if let value {
            defaults.set(value, forKey: key.rawValue)
        } else {
            defaults.removeObject(forKey: key.rawValue)
        }
    }

    public func codable<T: Decodable>(_ type: T.Type, _ key: Key) -> T? {
        guard let data = defaults.data(forKey: key.rawValue) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    public func setCodable<T: Encodable>(_ value: T?, for key: Key) {
        if let value, let data = try? JSONEncoder().encode(value) {
            defaults.set(data, forKey: key.rawValue)
        } else {
            defaults.removeObject(forKey: key.rawValue)
        }
    }

    public func remove(_ key: Key) {
        defaults.removeObject(forKey: key.rawValue)
    }
}
