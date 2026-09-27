import Foundation

public enum APIConfig {
    /// Override with the `WRITEOPIA_BASE_URL` environment variable (e.g. a local gateway).
    public static var baseURL: URL {
        if let override = ProcessInfo.processInfo.environment["WRITEOPIA_BASE_URL"],
           let url = URL(string: override) {
            return url
        }
        return URL(string: "https://writeopia.io")!
    }
}
