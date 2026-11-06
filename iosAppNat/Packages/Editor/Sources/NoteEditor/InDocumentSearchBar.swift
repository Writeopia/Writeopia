import SwiftUI
import WrDesign

/// The search inside the open document: the words, which match is active out of how many,
/// previous and next, and a close button. Mirrors `InDocumentSearchBar` of the Compose app.
struct InDocumentSearchBar: View {
    /// About how tall the bar is with its paddings, for what has to stay clear of it.
    static let height: CGFloat = 48

    @Bindable var viewModel: NoteEditorViewModel
    @FocusState private var isFocused: Bool

    var body: some View {
        HStack(spacing: 4) {
            TextField("Find in document", text: $viewModel.searchText)
                .textFieldStyle(.plain)
                .focused($isFocused)
                .onSubmit { viewModel.nextSearchResult() }
                .frame(minWidth: 140, maxWidth: 260)
                .accessibilityIdentifier("editor.search.field")

            Text(counter)
                .font(.caption)
                .monospacedDigit()
                .foregroundStyle(WrColors.textLighter)
                .lineLimit(1)
                .frame(minWidth: 64, alignment: .trailing)

            Button {
                viewModel.previousSearchResult()
            } label: {
                Image(systemName: "chevron.up")
            }
            .accessibilityLabel("Previous result")
            .disabled(viewModel.searchResultCount == 0)

            Button {
                viewModel.nextSearchResult()
            } label: {
                Image(systemName: "chevron.down")
            }
            .accessibilityLabel("Next result")
            .disabled(viewModel.searchResultCount == 0)

            Button {
                withAnimation(.snappy) { viewModel.hideSearch() }
            } label: {
                Image(systemName: "xmark")
            }
            .accessibilityLabel("Close search")
            .accessibilityIdentifier("editor.search.close")
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
        // A turn later: on appearing, the text view that had the keyboard still takes it back.
        .onAppear { DispatchQueue.main.async { isFocused = true } }
    }

    private var counter: String {
        let total = viewModel.searchResultCount
        return total == 0
            ? String(localized: "No results")
            : String(localized: "\(viewModel.searchResultIndex + 1) of \(total)")
    }
}
