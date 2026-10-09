import Foundation
import WrModels
import WrNetwork
import WrStorage

public struct AuthResponse: Decodable, Sendable {
    public let accessToken: String?
    public let refreshToken: String?
    public let writeopiaUser: User
    public let enabled: Bool

    enum CodingKeys: String, CodingKey {
        case accessToken, refreshToken, writeopiaUser, enabled
    }

    public init(accessToken: String?, refreshToken: String?, writeopiaUser: User, enabled: Bool) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.writeopiaUser = writeopiaUser
        self.enabled = enabled
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        accessToken = try container.decodeIfPresent(String.self, forKey: .accessToken)
        refreshToken = try container.decodeIfPresent(String.self, forKey: .refreshToken)
        writeopiaUser = try container.decode(User.self, forKey: .writeopiaUser)
        enabled = try container.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
    }
}

public struct RegisterResponse: Decodable, Sendable {
    public let writeopiaUser: User
    public let emailConfirmationRequired: Bool
    /// Present when the account was created for an already verified email, so it is signed in.
    public let accessToken: String?
    public let refreshToken: String?
}

struct StatusResponse: Decodable {
    let success: Bool
    let message: String?
}

public enum LoginResult: Equatable, Sendable {
    case loggedIn(User)
    /// The account exists but the email was never confirmed.
    case emailNotConfirmed(User)
}

public enum AuthError: Error, Equatable {
    case invalidCredentials
    case accountDeletionPending
    case invalidCode
    case server(String)

    public var userMessage: String {
        switch self {
        case .invalidCredentials: String(localized: "Wrong email or password.")
        case .accountDeletionPending: String(localized: "This account is being deleted.")
        case .invalidCode: String(localized: "The code is invalid or has expired.")
        case .server(let message): message
        }
    }
}

/// Calls to the Writeopia auth service.
public final class AuthAPI {
    private let client: APIClient
    private let tokenStore: TokenStore

    public init(client: APIClient, tokenStore: TokenStore) {
        self.client = client
        self.tokenStore = tokenStore
    }

    public func login(email: String, password: String) async throws -> LoginResult {
        struct Body: Encodable {
            let identifier: String
            let password: String
        }

        do {
            let response: AuthResponse = try await client.send(
                .post,
                "api/auth/login",
                body: Body(identifier: email.trimmingCharacters(in: .whitespaces), password: password),
                authenticated: false
            )
            return try handle(response)
        } catch APIError.unauthorized {
            throw AuthError.invalidCredentials
        } catch APIError.forbidden {
            throw AuthError.accountDeletionPending
        }
    }

    /// First step of sign-up: emails a verification code. The answer is the same whether or not
    /// the email already has an account.
    public func sendRegisterCode(email: String) async throws {
        struct Body: Encodable { let email: String }

        let response: StatusResponse = try await client.send(
            .post,
            "api/auth/register/email/send",
            body: Body(email: Self.normalized(email)),
            authenticated: false
        )
        if !response.success {
            throw AuthError.server(response.message ?? String(localized: "Could not send the verification code."))
        }
    }

    /// Second step of sign-up: checks the code. It is not consumed, `register` sends it again.
    public func verifyRegisterCode(email: String, code: String) async throws {
        struct Body: Encodable {
            let email: String
            let code: String
        }

        do {
            let _: StatusResponse = try await client.send(
                .post,
                "api/auth/register/email/verify",
                body: Body(email: Self.normalized(email), code: code),
                authenticated: false
            )
        } catch APIError.badRequest {
            throw AuthError.invalidCode
        }
    }

    /// Last step of sign-up: creates the account for the verified email and signs in.
    public func register(
        name: String,
        email: String,
        username: String,
        workspaceName: String,
        password: String,
        verificationCode: String
    ) async throws -> LoginResult {
        struct Body: Encodable {
            let name: String
            let email: String
            let username: String
            let workspaceName: String
            let password: String
            let verificationCode: String
        }

        let response: RegisterResponse
        do {
            response = try await client.send(
                .post,
                "api/auth/register",
                body: Body(
                    name: name,
                    email: Self.normalized(email),
                    username: username,
                    workspaceName: workspaceName,
                    password: password,
                    verificationCode: verificationCode
                ),
                authenticated: false
            )
        } catch APIError.conflict {
            throw AuthError.server(String(localized: "An account with this email or username already exists."))
        }

        guard !response.emailConfirmationRequired, let accessToken = response.accessToken else {
            return .emailNotConfirmed(response.writeopiaUser)
        }

        tokenStore.save(accessToken: accessToken, refreshToken: response.refreshToken)
        return .loggedIn(response.writeopiaUser)
    }

    private static func normalized(_ email: String) -> String {
        email.trimmingCharacters(in: .whitespaces).lowercased()
    }

    public func confirmEmail(email: String, code: String) async throws -> LoginResult {
        struct Body: Encodable {
            let email: String
            let code: String
        }

        do {
            let response: AuthResponse = try await client.send(
                .post,
                "api/auth/email/confirm",
                body: Body(email: email, code: code),
                authenticated: false
            )
            return try handle(response)
        } catch APIError.badRequest {
            throw AuthError.invalidCode
        }
    }

    public func resendConfirmation(email: String) async throws {
        struct Body: Encodable { let email: String }
        let _: StatusResponse = try await client.send(
            .post, "api/auth/email/resend", body: Body(email: email), authenticated: false
        )
    }

    public func requestPasswordReset(email: String) async throws {
        struct Body: Encodable { let email: String }
        let response: StatusResponse = try await client.send(
            .post, "api/auth/password/forgot", body: Body(email: email), authenticated: false
        )
        if !response.success {
            throw AuthError.server(response.message ?? String(localized: "Could not send the reset code."))
        }
    }

    public func verifyResetCode(email: String, code: String) async throws {
        struct Body: Encodable {
            let email: String
            let code: String
        }

        do {
            let _: StatusResponse = try await client.send(
                .post, "api/auth/password/verify-code", body: Body(email: email, code: code), authenticated: false
            )
        } catch APIError.badRequest {
            throw AuthError.invalidCode
        }
    }

    public func resetPassword(email: String, code: String, newPassword: String) async throws {
        struct Body: Encodable {
            let email: String
            let code: String
            let newPassword: String
        }

        do {
            let _: StatusResponse = try await client.send(
                .post,
                "api/auth/password/reset-with-code",
                body: Body(email: email, code: code, newPassword: newPassword),
                authenticated: false
            )
        } catch APIError.badRequest {
            throw AuthError.invalidCode
        }
    }

    public func currentUser() async throws -> User {
        try await client.get("api/auth/user/current")
    }

    /// Revokes the refresh token on the server and forgets the local tokens. Local tokens are
    /// cleared even when the server can't be reached.
    public func logout() async {
        struct Body: Encodable { let refreshToken: String }

        if let refreshToken = tokenStore.refreshToken {
            try? await client.perform(.post, "api/auth/logout", body: Body(refreshToken: refreshToken))
        }
        tokenStore.clear()
    }

    /// Changes the password of the signed in user.
    public func changePassword(newPassword: String) async throws {
        struct Body: Encodable { let newPassword: String }
        try await client.perform(.put, "api/auth/password/reset", body: Body(newPassword: newPassword))
    }

    public func deleteAccount() async throws {
        try await client.perform(.delete, "api/auth/account")
        tokenStore.clear()
    }

    private func handle(_ response: AuthResponse) throws -> LoginResult {
        guard response.enabled, let accessToken = response.accessToken else {
            return .emailNotConfirmed(response.writeopiaUser)
        }

        tokenStore.save(accessToken: accessToken, refreshToken: response.refreshToken)
        return .loggedIn(response.writeopiaUser)
    }
}
