import Foundation

public enum PasswordStrength: Int, Comparable {
    case none
    case weak
    case medium
    case strong

    public static func < (lhs: PasswordStrength, rhs: PasswordStrength) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

public struct PasswordValidation: Equatable {
    public let strength: PasswordStrength
    public let hasMinLength: Bool
    public let hasSpecialChar: Bool

    /// The backend only enforces the length; the special character keeps parity with the Compose app.
    public var isValid: Bool { hasMinLength && hasSpecialChar }
}

/// Same rules as `PasswordValidator` in the Compose auth feature.
public enum PasswordValidator {
    public static let minLength = 8
    private static let specialCharacters = CharacterSet(charactersIn: "!@#$%^&*()_+-=[]{}|;':\",./<>?`~\\")

    public static func validate(_ password: String) -> PasswordValidation {
        let hasMinLength = password.count >= minLength
        let hasSpecialChar = password.rangeOfCharacter(from: specialCharacters) != nil

        let strength: PasswordStrength = if password.isEmpty {
            .none
        } else if hasMinLength && hasSpecialChar {
            .strong
        } else if hasMinLength || hasSpecialChar {
            .medium
        } else {
            .weak
        }

        return PasswordValidation(strength: strength, hasMinLength: hasMinLength, hasSpecialChar: hasSpecialChar)
    }
}

enum FieldValidator {
    static func isValidEmail(_ email: String) -> Bool {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        return trimmed.wholeMatch(of: /[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}/) != nil
    }

    /// 3 to 30 letters, digits, `-` or `_`, as required by the backend.
    static func isValidUsername(_ username: String) -> Bool {
        username.wholeMatch(of: /[A-Za-z0-9_\-]{3,30}/) != nil
    }

    static func isValidWorkspaceName(_ name: String) -> Bool {
        (3...30).contains(name.trimmingCharacters(in: .whitespaces).count)
    }
}
