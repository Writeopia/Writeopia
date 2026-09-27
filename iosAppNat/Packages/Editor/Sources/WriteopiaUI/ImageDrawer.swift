#if canImport(UIKit)
import SwiftUI
import UIKit
import WrModels

/// An image of the document, like `ImageDrawer` of the SDK: from its URL when it was uploaded,
/// otherwise from the file on this device.
struct ImageDrawer: View {
    let step: StoryStep
    let isUploading: Bool

    var body: some View {
        content
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(alignment: .topTrailing) {
                if isUploading {
                    ProgressView()
                        .padding(8)
                        .background(.regularMaterial, in: Circle())
                        .padding(8)
                        .accessibilityLabel("Uploading image")
                }
            }
            .padding(.vertical, 4)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Image")
            .accessibilityIdentifier("image.\(step.id)")
    }

    @ViewBuilder
    private var content: some View {
        if let image = ImageFiles.localImage(path: step.path) {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity)
        } else if let url = step.url.flatMap(URL.init(string:)) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFit().frame(maxWidth: .infinity)
                case .failure:
                    unavailable
                default:
                    ProgressView()
                        .frame(maxWidth: .infinity, minHeight: 160)
                        .background(Color(uiColor: .secondarySystemFill))
                }
            }
        } else {
            unavailable
        }
    }

    /// Images saved on another device only have a path that doesn't exist here.
    private var unavailable: some View {
        Label("Image not available", systemImage: "photo")
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, minHeight: 120)
            .background(Color(uiColor: .secondarySystemFill))
    }
}

/// Where the editor keeps the images added on this device.
public enum ImageFiles {
    public static var directory: URL {
        URL.applicationSupportDirectory.appending(path: "Writeopia/Images", directoryHint: .isDirectory)
    }

    /// Saves JPEG data and returns its path.
    public static func save(_ data: Data, name: String = UUID().uuidString) throws -> URL {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appending(path: "\(name).jpg")
        try data.write(to: url, options: .atomic)
        return url
    }

    /// The image at `path`. The app container moves between installs and updates, so a path
    /// that doesn't exist anymore is also looked up by file name in `directory`.
    static func localImage(path: String?) -> UIImage? {
        guard let path, !path.isEmpty else { return nil }
        if let cached = cache.object(forKey: path as NSString) {
            return cached
        }

        let moved = directory.appending(path: (path as NSString).lastPathComponent).path(percentEncoded: false)
        guard let image = UIImage(contentsOfFile: path) ?? UIImage(contentsOfFile: moved) else { return nil }
        cache.setObject(image, forKey: path as NSString)
        return image
    }

    /// Decoded images, so scrolling doesn't read and decode the files again.
    private static let cache = NSCache<NSString, UIImage>()
}
#endif
