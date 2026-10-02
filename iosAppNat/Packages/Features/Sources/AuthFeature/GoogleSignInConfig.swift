/// OAuth client IDs for Sign in with Google. Client IDs are public by design, so they are
/// committed here; paste the real values from the `writeopia` GCP project.
///
/// - `iosClientID`: the "iOS" OAuth client created for bundle `io.writeopia.WriteopiaNative`.
///   The same client serves the Mac build. Its reversed form must also be registered as a URL
///   scheme in `iOS-Info.plist`.
/// - `serverClientID`: the "Web application" OAuth client. Passing it as the server client ID
///   makes Google issue ID tokens whose audience is the web client, which is what the backend
///   verifies against.
public enum GoogleSignInConfig {
    public static let iosClientID = "REPLACE_ME_IOS.apps.googleusercontent.com"
    public static let serverClientID = "REPLACE_ME_WEB.apps.googleusercontent.com"

    /// The button is hidden until both IDs are filled in.
    public static var isConfigured: Bool {
        !iosClientID.hasPrefix("REPLACE_ME") && !serverClientID.hasPrefix("REPLACE_ME")
    }
}
