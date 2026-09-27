import SwiftUI

/// Canvas of the drawing editor: finger (or Apple Pencil) drags become strokes.
struct DrawingCanvas: View {
    let viewModel: DrawingViewModel
    let background: Color
    @State private var currentPoints: [DrawPoint] = []

    var body: some View {
        GeometryReader { geometry in
            Canvas { context, _ in
                StrokeRenderer.draw(viewModel.drawingData.strokes, in: &context, background: background)
                if !currentPoints.isEmpty {
                    StrokeRenderer.draw(viewModel.makeStroke(points: currentPoints), in: &context, background: background)
                }
            }
            .background(background)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .onChanged { value in
                        currentPoints.append(DrawPoint(
                            x: value.location.x,
                            y: value.location.y,
                            timestamp: Int64(Date().timeIntervalSince1970 * 1000)
                        ))
                    }
                    .onEnded { _ in
                        if !currentPoints.isEmpty {
                            viewModel.addStroke(viewModel.makeStroke(points: currentPoints))
                        }
                        currentPoints = []
                    }
            )
            .onAppear { viewModel.setCanvasSize(width: geometry.size.width, height: geometry.size.height) }
            .onChange(of: geometry.size) { _, size in
                viewModel.setCanvasSize(width: size.width, height: size.height)
            }
        }
        .accessibilityLabel("Drawing canvas")
        .accessibilityIdentifier("drawing.canvas")
    }
}
