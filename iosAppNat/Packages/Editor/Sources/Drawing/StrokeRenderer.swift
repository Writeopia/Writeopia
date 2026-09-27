import SwiftUI

/// Draws strokes like the Compose canvas: round caps and joins, the highlighter three times wider
/// at 40% opacity, and the eraser painting with the background color.
enum StrokeRenderer {
    static func draw(
        _ strokes: [Stroke],
        in context: inout GraphicsContext,
        background: Color,
        transform: CGAffineTransform = .identity,
        scale: CGFloat = 1
    ) {
        for stroke in strokes {
            draw(stroke, in: &context, background: background, transform: transform, scale: scale)
        }
    }

    static func draw(
        _ stroke: Stroke,
        in context: inout GraphicsContext,
        background: Color,
        transform: CGAffineTransform = .identity,
        scale: CGFloat = 1
    ) {
        guard let first = stroke.points.first else { return }

        var path = Path()
        path.move(to: CGPoint(x: first.x, y: first.y))
        if stroke.points.count == 1 {
            // A tap still leaves a dot.
            path.addLine(to: CGPoint(x: first.x + 0.01, y: first.y))
        }
        for point in stroke.points.dropFirst() {
            path.addLine(to: CGPoint(x: point.x, y: point.y))
        }

        let width = (stroke.tool == .highlighter ? stroke.strokeWidth * 3 : stroke.strokeWidth) * scale
        context.stroke(
            path.applying(transform),
            with: .color(color(of: stroke, background: background)),
            style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
        )
    }

    static func color(of stroke: Stroke, background: Color) -> Color {
        switch stroke.tool {
        case .eraser:
            return background
        case .highlighter:
            return swiftUIColor(stroke.color).opacity(0.4)
        case .pen:
            return swiftUIColor(stroke.color)
        }
    }

    static func swiftUIColor(_ argb: Int) -> Color {
        let components = DrawingColor.components(argb)
        return Color(.sRGB, red: components.red, green: components.green, blue: components.blue, opacity: components.alpha)
    }
}
