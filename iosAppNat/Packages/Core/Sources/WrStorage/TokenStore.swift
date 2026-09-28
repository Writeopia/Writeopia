import Foundation
import Security

/// Stores the tokens issued by the Writeopia auth service.
public protocol TokenStore: AnyObject {
    var accessToken: String? { get }
    var refreshToken: String? { get }
    func save(accessToken: String, refreshToken: String?)
    func clear()
}

/// Keychain backed `TokenStore`. Tokens survive app restarts but are only readable after the
/// first unlock of the device.
public final class KeychainTokenStore: TokenStore {
    private enum Key {
        static let accessToken = "accessToken"
        static let refreshToken = "refreshToken"
    }

    private let service: String

    public init(service: String = Bundle.main.bundleIdentifier ?? "io.writeopia.native") {
        self.service = service
    }

    public var accessToken: String? { read(Key.accessToken) }

    public var refreshToken: String? { read(Key.refreshToken) }

    public func save(accessToken: String, refreshToken: String?) {
        write(accessToken, for: Key.accessToken)

        if let refreshToken {
            write(refreshToken, for: Key.refreshToken)
        } else {
            delete(Key.refreshToken)
        }
    }

    public func clear() {
        delete(Key.accessToken)
        delete(Key.refreshToken)
    }

    private func baseQuery(_ key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
    }

    private func read(_ key: String) -> String? {
        var query = baseQuery(key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data
        else { return nil }

        return String(data: data, encoding: .utf8)
    }

    private func write(_ value: String, for key: String) {
        let data = Data(value.utf8)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]

        let status = SecItemUpdate(baseQuery(key) as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            let query = baseQuery(key).merging(attributes) { _, new in new }
            SecItemAdd(query as CFDictionary, nil)
        }
    }

    private func delete(_ key: String) {
        SecItemDelete(baseQuery(key) as CFDictionary)
    }
}

/// In memory `TokenStore`, used by previews and tests.
public final class InMemoryTokenStore: TokenStore {
    public private(set) var accessToken: String?
    public private(set) var refreshToken: String?

    public init(accessToken: String? = nil, refreshToken: String? = nil) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
    }

    public func save(accessToken: String, refreshToken: String?) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
    }

    public func clear() {
        accessToken = nil
        refreshToken = nil
    }
}
