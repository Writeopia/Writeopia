import Foundation
import Testing
import Drawing
@testable import NoteEditor
import Writeopia
import WriteopiaUI
import WrData
import WrModels
import WrNetwork

/// The backend of the equations, answering what the test says.
final class FakeEquations: EquationServing {
    var equation = Equation(latex: "x^2", imageUrl: "https://cdn.test/equations/x.png")
    var error: Error?
    private(set) var recognized: [String] = []
    private(set) var rendered: [String] = []

    func recognizeEquation(drawingJson: String) async throws -> Equation {
        recognized.append(drawingJson)
        if let error { throw error }
        return equation
    }

    func renderEquation(latex: String) async throws -> Equation {
        rendered.append(latex)
        if let error { throw error }
        return Equation(latex: latex, imageUrl: equation.imageUrl)
    }
}

@MainActor
struct EquationTests {
    private let drawingJson = DrawingData(strokes: [Stroke(points: [DrawPoint(x: 0, y: 0), DrawPoint(x: 10, y: 10)])]).toJson()

    private func document() -> WrDocument {
        WrDocument(id: "d", title: "Math", workspaceId: "w", content: [
                StoryStep(id: "t", type: .title, text: "Math", position: Rank.at(0)),
                StoryStep(id: "drawing", type: .drawing, text: drawingJson, position: Rank.at(1)),
                StoryStep(id: "equation", type: .equation, text: "a+b", url: "https://cdn.test/equations/ab.png", position: Rank.at(2)),
        ])
    }

    private func viewModel(_ equations: FakeEquations?) async -> NoteEditorViewModel {
        let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document()), equations: equations)
        await viewModel.loadDocument()
        return viewModel
    }

    @Test func aDrawingBecomesTheEquationTheCloudReadsAndUndoBringsItBack() async throws {
        let equations = FakeEquations()
        let viewModel = await viewModel(equations)

        viewModel.convertDrawingToEquation(stepId: "drawing")
        try await waitUntil { viewModel.writeopiaManager.step(withId: "drawing")?.type.number == StoryType.equation.number }

        let step = viewModel.writeopiaManager.step(withId: "drawing")!
        #expect(step.text == "x^2")
        #expect(step.url == "https://cdn.test/equations/x.png")
        #expect(equations.recognized == [drawingJson])
        #expect(viewModel.writeopiaManager.convertingDrawingStepIds.isEmpty)

        viewModel.writeopiaManager.undo()
        let restored = viewModel.writeopiaManager.step(withId: "drawing")!
        #expect(restored.type.number == StoryType.drawing.number)
        #expect(restored.text == drawingJson)
    }

    @Test func aFailedReadingKeepsTheDrawingAndTellsTheUser() async throws {
        let equations = FakeEquations()
        equations.error = APIError.badRequest("No equation was found in the drawing")
        let viewModel = await viewModel(equations)

        viewModel.convertDrawingToEquation(stepId: "drawing")
        try await waitUntil { viewModel.equationError != nil }

        #expect(viewModel.writeopiaManager.step(withId: "drawing")?.type.number == StoryType.drawing.number)
        #expect(viewModel.equationError == "No equation was found in the drawing")
    }

    @Test func withoutTheCloudNothingIsConverted() async {
        let viewModel = await viewModel(nil)

        viewModel.convertDrawingToEquation(stepId: "drawing")

        #expect(viewModel.equationError != nil)
        #expect(viewModel.writeopiaManager.step(withId: "drawing")?.type.number == StoryType.drawing.number)
    }

    @Test func editingAnEquationRendersTheNewLatex() async throws {
        let equations = FakeEquations()
        let viewModel = await viewModel(equations)

        viewModel.editEquation(stepId: "equation")
        #expect(viewModel.equationEdition == EquationEdition(stepId: "equation", latex: "a+b"))

        viewModel.equationEdition?.latex = " \\frac{a}{b} "
        viewModel.saveEquation()
        try await waitUntil { viewModel.writeopiaManager.step(withId: "equation")?.text == "\\frac{a}{b}" }

        #expect(viewModel.equationEdition == nil)
        #expect(equations.rendered == ["\\frac{a}{b}"])
        #expect(viewModel.writeopiaManager.step(withId: "equation")?.url == "https://cdn.test/equations/x.png")
    }

    @Test func equationsAreExportedBetweenDoubleDollars() {
        let markdown = DocumentToMarkdown.parse(document().bodySteps)

        #expect(markdown.contains("$$a+b$$"))
    }

    private func waitUntil(_ condition: @escaping @MainActor () -> Bool) async throws {
        for _ in 0..<200 where !condition() {
            try await Task.sleep(for: .milliseconds(10))
        }
        #expect(condition())
    }
}
