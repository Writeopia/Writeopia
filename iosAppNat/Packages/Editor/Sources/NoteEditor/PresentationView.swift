import SwiftUI
import WriteopiaUI
import WrDesign
import WrModels

/// Shows a presentation one slide at a time, like `PresentationScreen` of the Compose app:
/// each slide is drawn by the Writeopia editor, read only and centered on the screen, with the
/// arrows below to move between slides. The arrow keys and the space bar move too.
public struct PresentationView: View {
    private let presentation: Presentation
    private let managers: [WriteopiaStateManager]
    @State private var index = 0

    public init(presentation: Presentation) {
        self.presentation = presentation
        managers = presentation.slides.enumerated().map { offset, slide in
            let manager = WriteopiaStateManager()
            manager.isEditable = false
            manager.loadDocument(slide.asDocument(id: "\(presentation.id)-\(offset)"))
            return manager
        }
    }

    private var count: Int { managers.count }

    public var body: some View {
        ZStack {
            WrColors.systemBackground
                .ignoresSafeArea()
            if managers.isEmpty {
                ContentUnavailableView("This presentation has no slides", systemImage: "rectangle.on.rectangle.slash")
            } else {
                WriteopiaEditor(manager: managers[index], layout: .slide)
                    .id(index)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: index)
        .overlay(alignment: .bottom) {
            if !managers.isEmpty { controls }
        }
        .background { keyboardShortcuts }
        .navigationTitle(presentation.title)
    }

    private var controls: some View {
        HStack(spacing: 16) {
            Button(action: previous) {
                Image(systemName: "chevron.left.circle.fill")
                    .font(.system(size: 34))
            }
            .buttonStyle(.plain)
            .disabled(index == 0)
            .accessibilityLabel("Previous slide")
            .accessibilityIdentifier("presentation.previous")

            Text("\(index + 1) / \(count)")
                .font(.footnote.weight(.medium))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("presentation.counter")

            Button(action: next) {
                Image(systemName: "chevron.right.circle.fill")
                    .font(.system(size: 34))
            }
            .buttonStyle(.plain)
            .disabled(index >= count - 1)
            .accessibilityLabel("Next slide")
            .accessibilityIdentifier("presentation.next")
        }
        .foregroundStyle(.secondary)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(.regularMaterial, in: Capsule())
        .padding(.bottom, 24)
    }

    private var keyboardShortcuts: some View {
        ZStack {
            Button("Previous slide", action: previous)
                .keyboardShortcut(.leftArrow, modifiers: [])
            Button("Next slide", action: next)
                .keyboardShortcut(.rightArrow, modifiers: [])
            Button("Next slide", action: next)
                .keyboardShortcut(.space, modifiers: [])
        }
        .opacity(0)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func previous() {
        guard index > 0 else { return }
        index -= 1
    }

    private func next() {
        guard index < count - 1 else { return }
        index += 1
    }
}
