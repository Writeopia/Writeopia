import Foundation

public enum APIError: Error, Equatable {
    /// The session is no longer valid, even after trying to refresh it.
    case unauthorized
    case forbidden(String?)
    case notFound
    case conflict(String?)
    case badRequest(String?)
    case quotaExceeded
    case unexpectedStatus(Int)
    case decoding
    case offline

    public var userMessage: String {
        switch self {
        case .unauthorized: String(localized: "Your session has expired. Please sign in again.")
        case .forbidden(let message): message ?? String(localized: "You don't have permission to do that.")
        case .notFound: String(localized: "We couldn't find what you were looking for.")
        case .conflict(let message): message ?? String(localized: "This already exists.")
        case .badRequest(let message): message ?? String(localized: "The request was not valid.")
        case .quotaExceeded: String(localized: "You reached your AI quota for this month.")
        case .unexpectedStatus(let code): String(localized: "The server answered with an error (\(code)).")
        case .decoding: String(localized: "We couldn't read the server response.")
        case .offline: String(localized: "You seem to be offline. Check your connection and try again.")
        }
    }
}

public extension Error {
    /// Human readable message for any error thrown by the network layer.
    var userMessage: String {
        if let apiError = self as? APIError {
            return apiError.userMessage
        }
        if let urlError = self as? URLError,
           [.notConnectedToInternet, .networkConnectionLost, .timedOut, .cannotFindHost].contains(urlError.code) {
            return APIError.offline.userMessage
        }
        return String(localized: "Something went wrong. Please try again.")
    }
}
