import Foundation
import Testing
@testable import Drawing

@Suite struct DrawingDataTests {
    /// What kotlinx.serialization writes for a drawing made on Android: default values are
    /// skipped and colors are signed ARGB integers.
    let kotlinJson = #"""
    {"id":"d1","strokes":[
      {"id":"s1","points":[{"x":10.0,"y":20.0},{"x":30.5,"y":40.0,"pressure":0.5,"timestamp":1700}]},
      {"id":"s2","points":[{"x":0.0,"y":0.0}],"color":-65536,"strokeWidth":8.0,"tool":"HIGHLIGHTER"}
    ],"width":400,"height":600}
    """#

    @Test func readsDrawingsFromAndroid() throws {
        let drawing = try #require(DrawingData.fromJson(kotlinJson))

        #expect(drawing.width == 400)
        #expect(drawing.strokes[0].color == Stroke.black)
        #expect(drawing.strokes[0].tool == .pen)
        #expect(drawing.strokes[0].strokeWidth == 4)
        #expect(drawing.strokes[0].points[1] == DrawPoint(x: 30.5, y: 40, pressure: 0.5, timestamp: 1700))
        #expect(drawing.strokes[1].tool == .highlighter)
        #expect(DrawingColor.components(drawing.strokes[1].color) == (1, 0, 0, 1))
    }

    @Test func roundTripsThroughJson() throws {
        let drawing = DrawingData(
            id: "d",
            strokes: [Stroke(id: "s", points: [DrawPoint(x: 1, y: 2)], color: DrawingColor.argb(0xFF00_00FF), strokeWidth: 12, tool: .eraser)],
            width: 100,
            height: 200
        )

        let json = drawing.toJson()
        #expect(DrawingData.fromJson(json) == drawing)
        #expect(json.contains(#""tool":"ERASER""#))
        #expect(json.contains(#""color":-16776961"#))
        #expect(!json.contains("backgroundColor"))
    }

    @Test func invalidJsonIsNotADrawing() {
        #expect(DrawingData.fromJson("not json") == nil)
        #expect(DrawingData.fromJson(nil) == nil)
    }

    @Test func boundsCoverTheStrokesWithPadding() throws {
        let drawing = DrawingData(strokes: [
            Stroke(points: [DrawPoint(x: 100, y: 50), DrawPoint(x: 200, y: 150)]),
            Stroke(points: [DrawPoint(x: 120, y: 300)]),
        ])

        let bounds = try #require(drawing.bounds())
        #expect(bounds == CGRect(x: 60, y: 10, width: 180, height: 330))
        #expect(DrawingData().bounds() == nil)
        #expect(DrawingData().isEmpty)
    }

    @Test func presetsMatchTheComposeToolbar() {
        #expect(DrawingColor.presets.count == 11)
        #expect(DrawingColor.presets.first == Stroke.black)
        #expect(DrawingColor.strokeWidths == [4, 8, 12, 16, 20])
    }
}

@Suite struct DrawingViewModelTests {
    @Test func strokesUseTheCurrentToolColorAndWidth() {
        let viewModel = DrawingViewModel(defaultColor: DrawingColor.argb(0xFFFF_0000))
        viewModel.currentTool = .highlighter
        viewModel.strokeWidth = 16

        viewModel.addStroke(viewModel.makeStroke(points: [DrawPoint(x: 1, y: 1)]))

        let stroke = viewModel.drawingData.strokes[0]
        #expect(stroke.tool == .highlighter)
        #expect(stroke.strokeWidth == 16)
        #expect(stroke.color == DrawingColor.argb(0xFFFF_0000))
    }

    @Test func undoRevertsStrokesAndClear() {
        let viewModel = DrawingViewModel()
        #expect(!viewModel.canUndo)

        viewModel.addStroke(Stroke(id: "a"))
        viewModel.addStroke(Stroke(id: "b"))
        viewModel.clear()
        #expect(viewModel.drawingData.strokes.isEmpty)

        viewModel.undo()
        #expect(viewModel.drawingData.strokes.map(\.id) == ["a", "b"])
        viewModel.undo()
        viewModel.undo()
        #expect(viewModel.drawingData.strokes.isEmpty)
        #expect(!viewModel.canUndo)
    }

    @Test func editingKeepsTheOriginalDrawing() {
        let original = DrawingData(id: "d", strokes: [Stroke(id: "a")], width: 10, height: 10)
        let viewModel = DrawingViewModel(drawing: original)

        viewModel.setCanvasSize(width: 390, height: 700)

        #expect(viewModel.drawingData.id == "d")
        #expect(viewModel.drawingData.strokes.map(\.id) == ["a"])
        #expect(viewModel.drawingData.width == 390)
    }
}
