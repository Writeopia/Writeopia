import CoreGraphics
import Foundation
import ImageIO

/// A decoded picture handed between tasks. `CGImage` is immutable.
nonisolated struct HeaderPicture: @unchecked Sendable {
    let image: CGImage
}

/// Loads the pictures of headers and cuts each one to its drawing (`HeaderPictureCrop`), once per
/// address: the editor band, the cards of a folder and the tiles of the picker share the result.
final class HeaderPictureLoader {
    static let shared = HeaderPictureLoader()

    private let cache = NSCache<NSURL, HeaderPictureBox>()
    private var inFlight: [URL: Task<HeaderPicture?, Never>] = [:]
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
        cache.countLimit = 100
    }

    func picture(at url: URL) async -> CGImage? {
        if let cached = cache.object(forKey: url as NSURL) { return cached.picture.image }
        if let running = inFlight[url] { return await running.value?.image }

        let session = self.session
        let task = Task<HeaderPicture?, Never> {
            guard let (data, _) = try? await session.data(from: url) else { return nil }
            return await Task.detached(priority: .userInitiated) { Self.decode(data) }.value
        }
        inFlight[url] = task
        let picture = await task.value
        inFlight[url] = nil
        if let picture { cache.setObject(HeaderPictureBox(picture), forKey: url as NSURL) }
        return picture?.image
    }

    nonisolated private static func decode(_ data: Data) -> HeaderPicture? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else { return nil }
        return HeaderPicture(image: HeaderPictureCrop.cropped(image))
    }
}

private final class HeaderPictureBox {
    let picture: HeaderPicture
    init(_ picture: HeaderPicture) { self.picture = picture }
}
