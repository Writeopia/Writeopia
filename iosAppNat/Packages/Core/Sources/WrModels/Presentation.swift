import Foundation

/// One slide of a presentation, like `SlidePage` of the Kotlin presentation plugin: a title and
/// the steps below it.
public struct Slide: Equatable, Sendable {
    public var title: String
    /// The content rows of the slide, positions from 1 up.
    public var steps: [StoryStep]

    public init(title: String, steps: [StoryStep] = []) {
        self.title = title
        self.steps = steps
    }

    /// The slide as a document the editor can load: the title as a `title` step at position 0,
    /// the content after it.
    public func asDocument(id: String, now: Int64 = Date.nowMillis) -> WrDocument {
        var content = [StoryStep(id: "\(id)-title", type: .title, text: title, position: 0, lastUpdatedAt: now)]
        for (offset, var step) in steps.enumerated() {
            step.position = Double(offset + 1)
            content.append(step)
        }
        return WrDocument(id: id, title: title, workspaceId: "", content: content, createdAt: now, lastUpdatedAt: now)
    }
}

/// A presentation the AI generated from a document. Kept on the device, one row per step.
public struct Presentation: Identifiable, Equatable, Sendable {
    public let id: String
    public var documentId: String
    public var title: String
    /// Epoch milliseconds.
    public var createdAt: Int64
    public var slides: [Slide]

    public init(id: String = UUID().uuidString, documentId: String, title: String, createdAt: Int64 = Date.nowMillis, slides: [Slide]) {
        self.id = id
        self.documentId = documentId
        self.title = title
        self.createdAt = createdAt
        self.slides = slides
    }
}

/// What the presentation window shows (`openWindow(value:)`).
public struct PresentationWindowRef: Codable, Hashable, Sendable {
    public let presentationId: String

    public init(presentationId: String) {
        self.presentationId = presentationId
    }
}
