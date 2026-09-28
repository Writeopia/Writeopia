import Foundation
import Observation

/// State of the drawing editor. Mirrors `DrawingViewModel` of the Compose drawing feature.
@Observable
public final class DrawingViewModel {
    public private(set) var drawingData: DrawingData
    public var currentTool: DrawingTool = .pen
    public var currentColor: Int
    public var strokeWidth: Double = 4

    @ObservationIgnored private var history: [[Stroke]] = []

    public init(drawing: DrawingData? = nil, defaultColor: Int = Stroke.black) {
        drawingData = drawing ?? DrawingData()
        currentColor = defaultColor
    }

    public var canUndo: Bool { !history.isEmpty }

    public func addStroke(_ stroke: Stroke) {
        history.append(drawingData.strokes)
        drawingData.strokes.append(stroke)
    }

    /// A stroke with the current tool, color and width.
    public func makeStroke(points: [DrawPoint]) -> Stroke {
        Stroke(points: points, color: currentColor, strokeWidth: strokeWidth, tool: currentTool)
    }

    public func undo() {
        guard let previous = history.popLast() else { return }
        drawingData.strokes = previous
    }

    public func clear() {
        guard !drawingData.strokes.isEmpty else { return }
        history.append(drawingData.strokes)
        drawingData.strokes = []
    }

    public func setCanvasSize(width: Double, height: Double) {
        drawingData.width = Int(width)
        drawingData.height = Int(height)
    }
}
