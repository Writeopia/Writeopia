#if os(macOS)
import NoteEditor
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrSession

/// The window of a presentation on the Mac, opened with `openWindow(value:)` and a
/// `PresentationWindowRef`. Lives here, next to the documents, because the app links the
/// features and not the editor directly.
public struct PresentationWindowScene: Scene {
    private let session: AppSession

    public init(session: AppSession) {
        self.session = session
    }

    public var body: some Scene {
        WindowGroup("Presentation", for: PresentationWindowRef.self) { $ref in
            if let ref {
                PresentationWindowView(presentationId: ref.presentationId)
                    .environment(session)
                    .preferredColorScheme(session.colorTheme.colorScheme)
                    .frame(minWidth: 800, minHeight: 500)
            }
        }
        .defaultSize(width: 1100, height: 700)
    }
}

/// Loads the presentation of the window from the backend or the device, wherever it was made.
struct PresentationWindowView: View {
    let presentationId: String
    @Environment(AppSession.self) private var session
    @State private var presentation: Presentation?
    @State private var isLoading = true

    var body: some View {
        Group {
            if let presentation {
                PresentationView(presentation: presentation)
            } else if isLoading {
                ProgressView()
                    .controlSize(.large)
            } else {
                ContentUnavailableView("Presentation not found", systemImage: "play.slash")
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(WrColors.systemBackground)
        .task(id: presentationId) {
            isLoading = true
            presentation = await session.presentation(id: presentationId)
            isLoading = false
        }
    }
}
#endif
