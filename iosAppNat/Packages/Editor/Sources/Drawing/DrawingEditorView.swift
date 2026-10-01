import SwiftUI
import WrDesign

/// Full screen drawing editor, like `DrawingScreen` of the Compose app: Cancel, Save, the
/// canvas and a toolbar with the tools, colors, widths, undo and clear.
public struct DrawingEditorView: View {
    @State private var viewModel: DrawingViewModel
    @State private var openPanel: Panel?
    @Environment(\.dismiss) private var dismiss
    private let onSave: (DrawingData) -> Void

    private enum Panel {
        case colors
        case widths
    }

    public init(drawing: DrawingData?, defaultColor: Int, onSave: @escaping (DrawingData) -> Void) {
        _viewModel = State(initialValue: DrawingViewModel(drawing: drawing, defaultColor: defaultColor))
        self.onSave = onSave
    }

    public var body: some View {
        NavigationStack {
            DrawingCanvas(viewModel: viewModel, background: WrColors.systemBackground)
                .ignoresSafeArea(edges: .bottom)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    toolbar
                }
                .navigationTitle("Drawing")
                .toolbarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save") {
                            onSave(viewModel.drawingData)
                            dismiss()
                        }
                        .fontWeight(.semibold)
                        .accessibilityIdentifier("drawing.save")
                    }
                }
        }
        .tint(WrColors.accent)
    }

    private var toolbar: some View {
        VStack(spacing: 8) {
            switch openPanel {
            case .colors:
                colorsPanel
            case .widths:
                widthsPanel
            case nil:
                EmptyView()
            }

            HStack(spacing: 2) {
                toolButton(.pen, systemImage: "pencil.tip", label: "Pen")
                toolButton(.highlighter, systemImage: "highlighter", label: "Highlighter")
                toolButton(.eraser, systemImage: "eraser", label: "Eraser")

                Divider().frame(height: 22).padding(.horizontal, 4)

                Button {
                    toggle(.colors)
                } label: {
                    Circle()
                        .fill(StrokeRenderer.swiftUIColor(viewModel.currentColor))
                        .overlay(Circle().strokeBorder(Color.primary.opacity(0.25)))
                        .frame(width: 24, height: 24)
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Color")

                Button {
                    toggle(.widths)
                } label: {
                    Circle()
                        .fill(Color.primary)
                        .frame(width: max(4, viewModel.strokeWidth), height: max(4, viewModel.strokeWidth))
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Stroke width")

                Divider().frame(height: 22).padding(.horizontal, 4)

                iconButton("arrow.uturn.backward", label: "Undo", action: viewModel.undo)
                    .disabled(!viewModel.canUndo)
                iconButton("trash", label: "Clear", action: viewModel.clear)
                    .disabled(viewModel.drawingData.strokes.isEmpty)
            }
            .padding(.horizontal, 10)
            .frame(height: 48)
            .drawingGlass()
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
        .animation(.snappy, value: openPanel)
    }

    private var colorsPanel: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(DrawingColor.presets, id: \.self) { color in
                    let isSelected = viewModel.currentColor == color
                    Button {
                        viewModel.currentColor = color
                        if viewModel.currentTool == .eraser { viewModel.currentTool = .pen }
                    } label: {
                        Circle()
                            .fill(StrokeRenderer.swiftUIColor(color))
                            .overlay(Circle().strokeBorder(Color.primary.opacity(isSelected ? 0.9 : 0.2), lineWidth: isSelected ? 2.5 : 1))
                            .frame(width: 28, height: 28)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(.horizontal, 12)
        }
        .frame(height: 44)
        .drawingGlass()
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private var widthsPanel: some View {
        HStack(spacing: 18) {
            ForEach(DrawingColor.strokeWidths, id: \.self) { width in
                let isSelected = viewModel.strokeWidth == width
                Button {
                    viewModel.strokeWidth = width
                } label: {
                    Circle()
                        .fill(isSelected ? WrColors.accent : Color.primary)
                        .frame(width: width, height: width)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Width \(Int(width))"))
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.horizontal, 16)
        .frame(height: 44)
        .drawingGlass()
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private func toggle(_ panel: Panel) {
        openPanel = openPanel == panel ? nil : panel
    }

    private func toolButton(_ tool: DrawingTool, systemImage: String, label: LocalizedStringKey) -> some View {
        let isSelected = viewModel.currentTool == tool
        return Button {
            viewModel.currentTool = tool
        } label: {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: isSelected ? .bold : .medium))
                .foregroundStyle(isSelected ? WrColors.accent : .primary)
                .frame(width: 40, height: 40)
                .background(Circle().fill(isSelected ? WrColors.accent.opacity(0.15) : Color.clear))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func iconButton(_ systemImage: String, label: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .medium))
                .frame(width: 40, height: 40)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

extension View {
    @ViewBuilder
    func drawingGlass() -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular.interactive(), in: .capsule)
        } else {
            background(.regularMaterial, in: Capsule())
                .overlay(Capsule().strokeBorder(Color.primary.opacity(0.08)))
                .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        }
    }
}
