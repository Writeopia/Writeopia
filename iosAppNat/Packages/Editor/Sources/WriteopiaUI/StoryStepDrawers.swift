#if canImport(UIKit)
import SwiftUI
import Writeopia
import WrDesign
import WrModels

/// Draws one `DrawStory`, picking the drawer of its type like the drawers map of the SDK.
/// Drawers the app provides for step types the editor doesn't know, like the drawers map the
/// Compose app merges into the SDK drawers (e.g. `DrawingPreviewDrawer`).
public typealias CustomStepDrawer = (StoryStep) -> AnyView

struct CustomStepDrawersKey: EnvironmentKey {
    static let defaultValue: [Int: CustomStepDrawer] = [:]
}

extension EnvironmentValues {
    var customStepDrawers: [Int: CustomStepDrawer] {
        get { self[CustomStepDrawersKey.self] }
        set { self[CustomStepDrawersKey.self] = newValue }
    }
}

struct StoryStepDrawer: View {
    let draw: DrawStory
    let manager: WriteopiaStateManager
    @Environment(\.customStepDrawers) private var customDrawers

    private var step: StoryStep { draw.storyStep }

    var body: some View {
        content
            // Dropping on a step places the dragged step right after it, like hovering a
            // step in the SDK highlights the space below it.
            .modifier(StepDropTarget(draw: draw, manager: manager))
    }

    @ViewBuilder
    private var content: some View {
        switch step.type.number {
        case StoryType.title.number:
            TitleDrawer(step: step, manager: manager)
        case StoryType.text.number:
            DraggableStep(step: step, position: draw.position, manager: manager) {
                StepTextView(step: step, manager: manager)
            }
        case StoryType.checkItem.number:
            DraggableStep(step: step, position: draw.position, manager: manager) {
                CheckItemDrawer(step: step, manager: manager)
            }
        case StoryType.unorderedListItem.number:
            DraggableStep(step: step, position: draw.position, manager: manager) {
                UnorderedListItemDrawer(step: step, manager: manager)
            }
        case StoryType.codeBlock.number:
            DraggableStep(step: step, position: draw.position, manager: manager) {
                CodeBlockDrawer(step: step, manager: manager)
            }
        case StoryType.aiAnswer.number:
            DraggableStep(step: step, position: draw.position, manager: manager) {
                AiAnswerDrawer(step: step, manager: manager)
            }
        case StoryType.documentLink.number:
            DraggableStep(step: step, position: draw.position, manager: manager) {
                DocumentLinkDrawer(step: step, manager: manager)
            }
        case StoryType.divider.number:
            DraggableStep(step: step, position: draw.position, manager: manager) {
                DividerDrawer()
            }
        case StoryType.loading.number:
            LoadingDrawer()
        case StoryType.space.number, StoryType.onDragSpace.number:
            SpaceDrawer(draw: draw, manager: manager)
        case StoryType.lastSpace.number:
            LastSpaceDrawer(draw: draw, manager: manager)
        default:
            if let custom = customDrawers[step.type.number] {
                DraggableStep(step: step, position: draw.position, manager: manager) {
                    custom(step)
                }
            }
        }
    }
}

/// Leading gutter shared by every step. The grip shows while the cursor is in the step, and
/// holding it starts a drag to reorder the step. The gutter keeps its width when the grip is
/// hidden so the text doesn't shift.
struct DraggableStep<Content: View>: View {
    let step: StoryStep
    let position: Double
    let manager: WriteopiaStateManager
    var alignment: VerticalAlignment = .center
    @ViewBuilder let content: Content

    var body: some View {
        HStack(alignment: alignment, spacing: 4) {
            Image(systemName: "line.3.horizontal")
                .font(.caption)
                .foregroundStyle(.tertiary)
                .frame(width: EditorLayout.gutter, height: 24)
                .contentShape(Rectangle())
                .draggable(manager.dragPayload(for: step)) {
                    DragPreview(step: step)
                }
                .accessibilityLabel("Reorder")
                .accessibilityIdentifier("drag.\(step.id)")
                .opacity(showsGrip ? 1 : 0)
                .allowsHitTesting(showsGrip)
                .accessibilityHidden(!showsGrip)
                .animation(.easeInOut(duration: 0.15), value: showsGrip)

            content
                .frame(maxWidth: .infinity, alignment: .leading)
                .blockDecoration(step)
        }
        .swipeToSelect(step, manager: manager)
    }

    private var showsGrip: Bool {
        manager.isEditable && manager.currentStory.focus == position
    }
}

enum EditorLayout {
    static let gutter: CGFloat = 20
}

struct TitleDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    var body: some View {
        StepTextView(step: step, manager: manager)
            .overlay(alignment: .topLeading) {
                if (step.text ?? "").isEmpty {
                    Text("Untitled")
                        .font(manager.fontFamily.font(size: 34, weight: .bold))
                        .foregroundStyle(.quaternary)
                        .allowsHitTesting(false)
                }
            }
            .padding(.leading, EditorLayout.gutter + 4)
            .padding(.vertical, 8)
    }
}

/// Monospaced text on a tinted background, like `CodeBlockDrawer` of the SDK.
struct CodeBlockDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    var body: some View {
        StepTextView(step: step, manager: manager)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(Color(uiColor: .secondarySystemFill), in: RoundedRectangle(cornerRadius: 8))
    }
}

extension View {
    /// Box (`HIGH_LIGHT_BLOCK`) and card (`CARD_BLOCK`) decorations of a step.
    @ViewBuilder
    func blockDecoration(_ step: StoryStep) -> some View {
        if step.hasTag(BlockTag.box.rawValue) {
            padding(10)
                .background(WrColors.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                .overlay {
                    RoundedRectangle(cornerRadius: 10)
                        .strokeBorder(WrColors.accent.opacity(0.35))
                }
        } else if step.hasTag(BlockTag.card.rawValue) {
            padding(12)
                .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                .shadow(color: .black.opacity(0.08), radius: 6, y: 2)
        } else {
            self
        }
    }
}

struct CheckItemDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Button {
                manager.onCheckedChange(stepId: step.id, checked: !(step.checked ?? false))
            } label: {
                Image(systemName: step.checked == true ? "checkmark.square.fill" : "square")
                    .foregroundStyle(step.checked == true ? WrColors.accent : Color.secondary)
                    .imageScale(.large)
            }
            .buttonStyle(.plain)
            .disabled(!manager.isEditable)
            .accessibilityLabel(step.checked == true ? "Checked" : "Unchecked")

            StepTextView(step: step, manager: manager)
        }
    }
}

struct UnorderedListItemDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text("•")
                .font(.body.bold())
                .foregroundStyle(.secondary)
            StepTextView(step: step, manager: manager)
        }
    }
}

/// Text written by the AI, shown in a card. It stays editable like any paragraph.
struct AiAnswerDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("AI", systemImage: "sparkles")
                .font(.caption.weight(.semibold))
                .foregroundStyle(WrColors.accent)
            StepTextView(step: step, manager: manager)
        }
        .padding(14)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
        .padding(.vertical, 8)
    }
}

struct DocumentLinkDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    private var title: String {
        step.documentLink?.title ?? step.text ?? "Linked document"
    }

    var body: some View {
        Button {
            if let link = step.documentLink {
                manager.onDocumentLinkClick?(link)
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "doc.text")
                Text(title)
                    .underline()
                    .multilineTextAlignment(.leading)
            }
            .font(.body.weight(.medium))
            .foregroundStyle(WrColors.accent)
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
        .disabled(step.documentLink == nil)
    }
}

struct DividerDrawer: View {
    var body: some View {
        // `Divider` would be vertical inside the gutter's HStack, so draw the line directly.
        Rectangle()
            .fill(Color(uiColor: .separator))
            .frame(maxWidth: .infinity)
            .frame(height: 1)
            .padding(.vertical, 12)
            .accessibilityLabel("Divider")
    }
}

struct LoadingDrawer: View {
    var body: some View {
        ProgressView()
            .controlSize(.small)
            .padding(.leading, EditorLayout.gutter + 5)
            .padding(.vertical, 6)
    }
}

/// Gap between steps. While a step is dragged over it, it turns into the `ON_DRAG_SPACE`
/// highlight that shows where the step will land.
struct SpaceDrawer: View {
    let draw: DrawStory
    let manager: WriteopiaStateManager

    private var isTarget: Bool { draw.storyStep.type.number == StoryType.onDragSpace.number }

    var body: some View {
        RoundedRectangle(cornerRadius: 3)
            .fill(isTarget ? Color.gray.opacity(0.6) : Color.clear)
            .frame(height: isTarget ? 6 : 4)
            .padding(.vertical, isTarget ? 2 : 3)
            .padding(.leading, EditorLayout.gutter)
            .contentShape(Rectangle().inset(by: -6))
            .stepDropDestination(after: draw.position, manager: manager)
            .animation(.easeOut(duration: 0.15), value: isTarget)
    }
}

/// Empty area after the last step: tapping it continues the document, and it accepts drops.
struct LastSpaceDrawer: View {
    let draw: DrawStory
    let manager: WriteopiaStateManager

    private var isTarget: Bool { draw.storyStep.type.number == StoryType.onDragSpace.number }

    var body: some View {
        VStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 3)
                .fill(isTarget ? Color.gray.opacity(0.6) : Color.clear)
                .frame(height: 6)
                .padding(.leading, EditorLayout.gutter)
            Color.clear
                .frame(maxWidth: .infinity, minHeight: 240)
        }
        .contentShape(Rectangle())
        .onTapGesture { manager.clickAtTheEnd() }
        .stepDropDestination(after: draw.position, manager: manager)
        .accessibilityIdentifier("editor.end")
    }
}

private struct DragPreview: View {
    let step: StoryStep

    var body: some View {
        Text(previewText)
            .font(.body)
            .lineLimit(2)
            .padding(10)
            .frame(maxWidth: 260, alignment: .leading)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
    }

    private var previewText: String {
        if step.type.number == StoryType.divider.number { return "Divider" }
        let text = step.documentLink?.title ?? step.text ?? ""
        return text.isEmpty ? "Empty line" : text
    }
}

private struct StepDropTarget: ViewModifier {
    let draw: DrawStory
    let manager: WriteopiaStateManager

    func body(content: Content) -> some View {
        let isSpace = [StoryType.space.number, StoryType.onDragSpace.number, StoryType.lastSpace.number]
            .contains(draw.storyStep.type.number)

        if isSpace {
            content
        } else {
            content.stepDropDestination(after: draw.position, manager: manager)
        }
    }
}

extension View {
    /// Accepts steps dropped here, moving them after the step at `position`.
    func stepDropDestination(after position: Double, manager: WriteopiaStateManager) -> some View {
        dropDestination(for: String.self) { payloads, _ in
            guard let payload = payloads.first else { return false }
            return manager.moveRequest(payload: payload, after: position)
        } isTargeted: { targeted in
            if targeted {
                manager.onDragHover(position)
            } else if manager.dragPosition == position {
                manager.onDragHover(nil)
            }
        }
    }
}
#endif
