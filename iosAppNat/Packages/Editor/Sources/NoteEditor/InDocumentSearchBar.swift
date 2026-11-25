import SwiftUI
import WrDesign

/// The search inside the open document: the words, which match is active out of how many,
/// previous and next, and a close button. Mirrors `InDocumentSearchBar` of the Compose app.
///
/// On the Mac it is a floating card under the toolbar; on iPhone and iPad a bar anchored to the
/// top of the editor, as wide as the screen, with the close button at its right end.
struct InDocumentSearchBar: View {
    /// About how tall the bar is with its paddings, for what has to stay clear of it.
    static var height: CGFloat {
        #if os(macOS)
        48
        #else
        57
        #endif
    }

    @Bindable var viewModel: NoteEditorViewModel
    @FocusState private var isFocused: Bool

    var body: some View {
        #if os(macOS)
        macBar
        #else
        mobileBar
        #endif
    }

    #if os(macOS)
    private var macBar: some View {
        HStack(spacing: 4) {
            field
                .frame(minWidth: 140, maxWidth: 260)

            counterText
                .frame(minWidth: 64, alignment: .trailing)

            previousButton
            nextButton
            closeButton
        }
        .buttonStyle(.borderless)
        .font(.callout)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(WrColors.secondaryBackground, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(WrColors.divider))
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .padding(.bottom, 4)
        .onAppear(perform: focus)
    }
    #else
    private var mobileBar: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                field
                    .font(.body)
                    .frame(maxWidth: .infinity, minHeight: Self.buttonSize)
                    .padding(.leading, 16)

                counterText
                    .frame(minWidth: 56, alignment: .trailing)
                    .padding(.horizontal, 8)

                previousButton
                    .frame(width: Self.buttonSize, height: Self.buttonSize)
                nextButton
                    .frame(width: Self.buttonSize, height: Self.buttonSize)
                closeButton
                    .frame(width: Self.buttonSize, height: Self.buttonSize)
                    .padding(.trailing, 4)
            }
            .buttonStyle(.borderless)
            .frame(height: 56)

            Divider()
        }
        .background(WrColors.secondaryBackground)
        .onAppear(perform: focus)
    }

    private static let buttonSize: CGFloat = 44
    #endif

    private var field: some View {
        TextField("Find in document", text: $viewModel.searchText)
            .textFieldStyle(.plain)
            .focused($isFocused)
            .submitLabel(.search)
            .onSubmit { viewModel.nextSearchResult() }
            .accessibilityIdentifier("editor.search.field")
    }

    private var counterText: some View {
        Text(counter)
            .font(.caption)
            .monospacedDigit()
            .foregroundStyle(WrColors.textLighter)
            .lineLimit(1)
    }

    private var previousButton: some View {
        iconButton("chevron.up", action: viewModel.previousSearchResult)
            .accessibilityLabel("Previous result")
            .disabled(viewModel.searchResultCount == 0)
    }

    private var nextButton: some View {
        iconButton("chevron.down", action: viewModel.nextSearchResult)
            .accessibilityLabel("Next result")
            .disabled(viewModel.searchResultCount == 0)
    }

    private var closeButton: some View {
        iconButton("xmark") { withAnimation(.snappy) { viewModel.hideSearch() } }
            .accessibilityLabel("Close search")
            .accessibilityIdentifier("editor.search.close")
    }

    /// On the phones the label fills the button's frame, so the whole 44 points answer the tap.
    private func iconButton(_ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            #if os(macOS)
            Image(systemName: systemImage)
            #else
            Image(systemName: systemImage)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
            #endif
        }
    }

    /// A turn later: on appearing, the text view that had the keyboard still takes it back.
    private func focus() {
        DispatchQueue.main.async { isFocused = true }
    }

    private var counter: String {
        let total = viewModel.searchResultCount
        return total == 0
            ? String(localized: "No results")
            : String(localized: "\(viewModel.searchResultIndex + 1) of \(total)")
    }
}
