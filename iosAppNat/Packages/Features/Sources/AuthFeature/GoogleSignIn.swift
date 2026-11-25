import CryptoKit
import Foundation
import Security
import WrNetwork

/// The Google OAuth client of the iOS and macOS app. Client IDs are public by design, so it is
/// committed here; the backend lists it among the audiences it accepts (`GoogleOAuthEnv`).
///
/// It is an "iOS" client (bundle id `io.writeopia.WriteopiaNative`), which also serves the Mac
/// app: Google gives such clients no secret and redirects to the reversed client id, a scheme
/// `ASWebAuthenticationSession` catches without registering it in the Info.plist.
enum GoogleOAuthConfig {
    static let clientID = "640912732949-8uafecltdrupleq1qc9j7sol60efv2p1.apps.googleusercontent.com"

    private static let suffix = ".apps.googleusercontent.com"

    /// False until a real client id is pasted in; the button stays hidden meanwhile.
    static var isConfigured: Bool {
        !clientID.hasPrefix("REPLACE_ME") && clientID.hasSuffix(suffix)
    }

    /// `com.googleusercontent.apps.<id>`: the client id reversed.
    static var callbackScheme: String {
        "com.googleusercontent.apps." + clientID.dropLast(suffix.count)
    }

    static var redirectURI: String { "\(callbackScheme):/oauthredirect" }
}

enum GoogleSignInError: Error, Equatable {
    /// The user closed the Google page or denied access; nothing to report.
    case cancelled
    case failed(String)
}

/// One sign-in attempt of the authorization code flow with PKCE (RFC 7636): the page to open,
/// then the code read from the redirect.
struct GoogleAuthorization {
    let clientID: String
    let redirectURI: String
    let verifier: String
    let state: String
    var authEndpoint = URL(string: "https://accounts.google.com/o/oauth2/v2/auth")!

    init(
        clientID: String = GoogleOAuthConfig.clientID,
        redirectURI: String = GoogleOAuthConfig.redirectURI,
        verifier: String = Self.randomString(bytes: 64),
        state: String = Self.randomString(bytes: 32)
    ) {
        self.clientID = clientID
        self.redirectURI = redirectURI
        self.verifier = verifier
        self.state = state
    }

    var url: URL {
        var components = URLComponents(url: authEndpoint, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid email profile"),
            URLQueryItem(name: "code_challenge", value: Self.challenge(verifier)),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "prompt", value: "select_account"),
        ]
        return components.url!
    }

    /// The authorization code of the redirect Google sent back.
    func code(from callback: URL) throws -> String {
        let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func value(_ name: String) -> String? { items.first { $0.name == name }?.value }

        if let error = value("error") {
            throw error == "access_denied" ? GoogleSignInError.cancelled : GoogleSignInError.failed(error)
        }
        guard value("state") == state else { throw GoogleSignInError.failed("OAuth state mismatch") }
        guard let code = value("code"), !code.isEmpty else {
            throw GoogleSignInError.failed("No authorization code received")
        }
        return code
    }

    /// S256 challenge: base64url(sha256(ascii(verifier))).
    static func challenge(_ verifier: String) -> String {
        base64URL(Data(SHA256.hash(data: Data(verifier.utf8))))
    }

    /// Random bytes, base64url encoded: 64 bytes make 86 characters, within PKCE's 43...128.
    static func randomString(bytes count: Int) -> String {
        var bytes = [UInt8](repeating: 0, count: count)
        _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        return base64URL(Data(bytes))
    }

    private static func base64URL(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

/// Exchanges the authorization code for Google's ID token, on the device: clients of the iOS type
/// have no secret, so unlike the desktop and web flows the backend is not needed for it. The ID
/// token then goes to `POST /api/auth/login/google`.
struct GoogleTokenExchanger {
    var transport: HTTPTransport = URLSession.shared
    var tokenEndpoint = URL(string: "https://oauth2.googleapis.com/token")!

    func idToken(code: String, authorization: GoogleAuthorization) async throws -> String {
        var form = URLComponents()
        form.queryItems = [
            URLQueryItem(name: "client_id", value: authorization.clientID),
            URLQueryItem(name: "code", value: code),
            URLQueryItem(name: "code_verifier", value: authorization.verifier),
            URLQueryItem(name: "redirect_uri", value: authorization.redirectURI),
            URLQueryItem(name: "grant_type", value: "authorization_code"),
        ]

        var request = URLRequest(url: tokenEndpoint)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        // `+` is not encoded by URLComponents but means a space in a form body.
        request.httpBody = Data(
            (form.percentEncodedQuery ?? "").replacingOccurrences(of: "+", with: "%2B").utf8
        )

        let (data, response) = try await transport.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]

        guard (200..<300).contains(status) else {
            let description = json?["error_description"] as? String ?? json?["error"] as? String
            throw GoogleSignInError.failed("Google code exchange failed: \(description ?? "\(status)")")
        }
        guard let idToken = json?["id_token"] as? String, !idToken.isEmpty else {
            throw GoogleSignInError.failed("Google token response has no id_token")
        }
        return idToken
    }
}
