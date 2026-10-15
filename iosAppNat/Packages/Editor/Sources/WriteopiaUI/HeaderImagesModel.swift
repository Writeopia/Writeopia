import Foundation
import Observation

/// Where the header pictures come from: the backend, drawing them with the cloud AI. Abstracted
/// so the editor can be tested without the network.
public protocol HeaderImageServing: AnyObject {
    /// Draws a header for a document, charged to the user. The result is the URL of a candidate.
    func generateHeaderImage(documentTitle: String, documentText: String) async throws -> String
    /// The URLs of the approved pictures of the workspace, the newest first.
    func headerImages() async throws -> [String]
    /// Keeps an approved picture with the pictures of the workspace.
    func saveHeaderImage(url: String) async throws
}

/// A header the AI draws, shown to the user to approve or to draw again.
public enum HeaderImageGeneration: Equatable {
    case idle
    case generating
    /// A drawn picture, already on the CDN, waiting for the user to approve it.
    case candidate(String)
    /// The AI drew nothing; the message says why when the backend said so.
    case failed(String?)
}

/// The pictures of the header menu, like `HeaderImagesController` of the Compose app: the ones of
/// the workspace, and the one the AI draws for the document. Drawing costs tokens of the user, so
/// a picture is only drawn when the user asks, one at a time.
@Observable
public final class HeaderImagesModel {
    public private(set) var images: [String] = []
    public private(set) var generation: HeaderImageGeneration = .idle

    @ObservationIgnored private let service: HeaderImageServing
    @ObservationIgnored private let documentTitle: () -> String
    @ObservationIgnored private let documentText: () -> String
    @ObservationIgnored private let onApproved: (String) -> Void
    @ObservationIgnored private var generationTask: Task<Void, Never>?

    public init(
        service: HeaderImageServing,
        documentTitle: @escaping () -> String,
        documentText: @escaping () -> String,
        onApproved: @escaping (String) -> Void
    ) {
        self.service = service
        self.documentTitle = documentTitle
        self.documentText = documentText
        self.onApproved = onApproved
    }

    public var isGenerating: Bool { generation == .generating }

    /// Fetches the pictures of the workspace again, each time the header menu opens.
    public func load() async {
        guard let fetched = try? await service.headerImages() else { return }
        // A picture just approved may not be saved on the backend yet: it stays in front.
        images = images.filter { !fetched.contains($0) } + fetched
    }

    /// Draws a picture for the document, or a different one in place of the candidate.
    public func generate() {
        guard !isGenerating else { return }
        generation = .generating
        let title = documentTitle()
        let text = documentText()

        generationTask = Task { [weak self, service] in
            let result: HeaderImageGeneration
            do {
                result = .candidate(try await service.generateHeaderImage(documentTitle: title, documentText: text))
            } catch {
                result = .failed(error.localizedDescription)
            }
            // Discarded while drawing: the answer is dropped.
            guard let self, !Task.isCancelled, self.isGenerating else { return }
            self.generation = result
        }
    }

    /// Puts the candidate in the header and keeps it with the pictures of the workspace.
    public func approve() {
        guard case .candidate(let url) = generation else { return }
        onApproved(url)
        images = [url] + images.filter { $0 != url }
        generation = .idle
        Task { [service] in try? await service.saveHeaderImage(url: url) }
    }

    /// Drops the candidate, or stops drawing it.
    public func discard() {
        generationTask?.cancel()
        generationTask = nil
        generation = .idle
    }

    /// Waits for the picture being drawn, for the tests.
    func waitForGeneration() async {
        await generationTask?.value
    }
}
