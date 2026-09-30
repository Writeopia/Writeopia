import Foundation
import WrModels

/// The presentations generated from the documents. They stay on the device: nothing is synced.
public protocol PresentationsRepository: AnyObject {
    /// The presentations of a document with their slides, newest first.
    func presentations(ofDocument documentId: String) async throws -> [Presentation]
    func presentation(id: String) async throws -> Presentation?
    func savePresentation(_ presentation: Presentation) async throws
    func deletePresentation(id: String) async throws
}
