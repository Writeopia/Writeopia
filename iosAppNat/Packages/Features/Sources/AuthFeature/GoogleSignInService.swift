import Foundation
import GoogleSignIn

#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

public enum GoogleSignInError: Error, Equatable {
    /// The user dismissed Google's sheet. Callers show nothing for this one.
    case cancelled
    case noPresenter
    case noIdToken
    case failed(String)
}

/// Obtains a Google ID token. Abstracted so view models can be tested without the SDK.
public protocol GoogleSignInProviding {
    func signIn() async throws -> String
}

/// Drives the GoogleSignIn-iOS SDK on iOS and macOS and returns the ID token for the backend.
public final class GoogleSignInService: GoogleSignInProviding {
    public init() {}

    public func signIn() async throws -> String {
        if GIDSignIn.sharedInstance.configuration == nil {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(
                clientID: GoogleSignInConfig.iosClientID,
                serverClientID: GoogleSignInConfig.serverClientID
            )
        }

        do {
            let result = try await Self.presentSignIn()
            guard let token = result.user.idToken?.tokenString else {
                throw GoogleSignInError.noIdToken
            }
            return token
        } catch let error as GoogleSignInError {
            throw error
        } catch {
            let nsError = error as NSError
            if nsError.domain == GIDSignInError.errorDomain,
               nsError.code == GIDSignInError.Code.canceled.rawValue {
                throw GoogleSignInError.cancelled
            }
            throw GoogleSignInError.failed(error.localizedDescription)
        }
    }

    private static func presentSignIn() async throws -> GIDSignInResult {
        #if os(iOS)
        guard let presenter = presentingViewController() else {
            throw GoogleSignInError.noPresenter
        }
        return try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        #elseif os(macOS)
        guard let window = NSApp.keyWindow ?? NSApp.windows.first(where: \.isVisible) else {
            throw GoogleSignInError.noPresenter
        }
        return try await GIDSignIn.sharedInstance.signIn(withPresenting: window)
        #endif
    }

    #if os(iOS)
    private static func presentingViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap(\.windows).first(where: \.isKeyWindow)
            ?? scenes.first?.windows.first
        var controller = window?.rootViewController
        while let presented = controller?.presentedViewController {
            controller = presented
        }
        return controller
    }
    #endif
}

/// Lets the app target forward incoming URLs to the SDK without importing it directly.
public enum GoogleSignInURLHandler {
    @discardableResult
    public static func handle(_ url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }
}
