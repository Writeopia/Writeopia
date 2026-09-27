import SwiftUI
import WrDesign

/// A drawing inside the document, like `DrawingPreviewDrawer` of the Compose app: cropped to
/// its strokes, scaled down to fit the width (never up), and tappable to edit it.
public struct DrawingPreview: View {
    private let drawing: DrawingData?
    private let onTap: () -> Void

    public init(json: String?, onTap: @escaping () -> Void) {
        drawing = DrawingData.fromJson(json)
        self.onTap = onTap
    }

    public var body: some View {
        Button(action: onTap) {
            content
                .frame(maxWidth: .infinity)
                .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 12))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .strokeBorder(WrColors.divider)
                }
                .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .padding(.vertical, 4)
        .accessibilityLabel("Drawing")
        .accessibilityHint("Opens the drawing")
        .accessibilityIdentifier("drawing.preview")
    }

    @ViewBuilder
    private var content: some View {
        if let drawing, let bounds = drawing.bounds(), !drawing.isEmpty {
            GeometryReader { geometry in
                let scale = min(geometry.size.width / bounds.width, 1)
                Canvas { context, size in
                    let offsetX = (size.width - bounds.width * scale) / 2
                    let transform = CGAffineTransform(translationX: offsetX, y: 0)
                        .scaledBy(x: scale, y: scale)
                        .translatedBy(x: -bounds.minX, y: -bounds.minY)
                    StrokeRenderer.draw(
                        drawing.strokes,
                        in: &context,
                        background: Color(uiColor: .systemBackground),
                        transform: transform,
                        scale: scale
                    )
                }
            }
            .aspectRatio(bounds.width / bounds.height, contentMode: .fit)
            .frame(maxHeight: bounds.height)
            .padding(8)
        } else {
            // An empty drawing still leaves something to tap.
            Label("Empty drawing", systemImage: "pencil.and.scribble")
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
        }
    }
}
