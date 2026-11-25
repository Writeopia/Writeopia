import SwiftUI
import WrDesign
import WrModels
import WrSession
import WriteopiaUI

/// The header pictures of the workspace, drawn with the AI for the documents, each with a button
/// to delete it. Deleting asks first. Like `SettingsHeaderImagesScreen` of the Compose app.
struct HeaderImagesSettingsView: View {
    @State private var model: HeaderImagesSettingsModel
    @State private var toDelete: HeaderImage?

    init(session: AppSession) {
        _model = State(initialValue: HeaderImagesSettingsModel(source: session.headerImages))
    }

    var body: some View {
        Group {
            if model.isAvailable {
                content
            } else {
                OfflineNotice(
                    title: "Header images need an account",
                    message: "Header images live in the cloud. Sign in to an online workspace to manage them."
                )
            }
        }
        .navigationTitle("Header images")
        .alert(
            "Delete header image?",
            isPresented: Binding(get: { toDelete != nil }, set: { if !$0 { toDelete = nil } }),
            presenting: toDelete
        ) { image in
            Button("Delete", role: .destructive) {
                Task { await model.delete(image) }
            }
            .accessibilityIdentifier("headerImages.confirmDelete")
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("It leaves the images of this workspace. Documents that use it as their header keep it.")
        }
    }

    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Pictures drawn with AI for the headers of this workspace. Deleting one takes it out of this list; documents that already use it keep it.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                WrErrorText(model.deleteError)

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 200), spacing: 12)], spacing: 12) {
                    ForEach(model.images, id: \.id) { image in
                        tile(image)
                    }
                }
            }
            .padding()
        }
        .overlay {
            if model.images.isEmpty {
                if model.isLoading || model.errorMessage != nil {
                    WrStateOverlay(
                        isLoading: model.isLoading,
                        isEmpty: true,
                        errorMessage: model.errorMessage,
                        emptyTitle: "No header images",
                        emptyImage: "photo.on.rectangle",
                        retry: { Task { await model.load() } }
                    )
                } else {
                    ContentUnavailableView {
                        Label("No header images", systemImage: "photo.on.rectangle")
                    } description: {
                        Text("Draw one with AI from the header of a document.")
                    }
                }
            }
        }
        .task { await model.load() }
        .refreshable { await model.load() }
        .animation(.default, value: model.images.map(\.id))
    }

    private func tile(_ image: HeaderImage) -> some View {
        let isDeleting = model.deleting.contains(image.id)

        return HeaderImageView(url: URL(string: image.url))
            .aspectRatio(16 / 9, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .opacity(isDeleting ? 0.4 : 1)
            .overlay {
                if isDeleting { ProgressView() }
            }
            .overlay(alignment: .topTrailing) {
                if !isDeleting {
                    Button {
                        toDelete = image
                    } label: {
                        Image(systemName: "trash")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.white)
                            .frame(width: 32, height: 32)
                            .background(.black.opacity(0.55), in: Circle())
                    }
                    .buttonStyle(.plain)
                    .padding(6)
                    .accessibilityLabel("Delete")
                    .accessibilityIdentifier("headerImages.delete.\(image.id)")
                }
            }
            .contextMenu {
                Button("Delete", systemImage: "trash", role: .destructive) { toDelete = image }
            }
            .accessibilityIdentifier("headerImages.image.\(image.id)")
    }
}
