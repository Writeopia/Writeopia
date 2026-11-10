import Foundation
import WrModels
import WrNetwork

/// Reads equations in drawings and renders LaTeX to pictures, like `EquationAi` of the Kotlin
/// app. Only the backend does either, so there is no on-device implementation. Abstracted so the
/// editor can be tested without the network.
public protocol EquationServing: AnyObject {
    /// The equation in the strokes of `drawingJson` (the `DrawingData` JSON of a `DRAWING` step).
    func recognizeEquation(drawingJson: String) async throws -> Equation
    /// The picture of `latex`.
    func renderEquation(latex: String) async throws -> Equation
}

/// The equation routes of the Writeopia backend (`/api/ai/equation/recognize` and
/// `/api/ai/equation/render`).
public final class EquationAPI: EquationServing {
    private let client: APIClient

    public init(client: APIClient) {
        self.client = client
    }

    public func recognizeEquation(drawingJson: String) async throws -> Equation {
        struct Body: Encodable {
            let drawing: String
        }
        let response: EquationResponse = try await client.send(
            .post,
            "api/ai/equation/recognize",
            body: Body(drawing: drawingJson)
        )
        return try equation(of: response, fallback: String(localized: "The equation could not be read"))
    }

    public func renderEquation(latex: String) async throws -> Equation {
        struct Body: Encodable {
            let latex: String
        }
        let response: EquationResponse = try await client.send(.post, "api/ai/equation/render", body: Body(latex: latex))
        return try equation(of: response, fallback: String(localized: "The equation could not be rendered"))
    }

    private func equation(of response: EquationResponse, fallback: String) throws -> Equation {
        guard let latex = response.latex, let url = response.imageUrl, !url.isEmpty else {
            throw APIError.badRequest(response.error ?? fallback)
        }
        return Equation(latex: latex, imageUrl: url)
    }
}
