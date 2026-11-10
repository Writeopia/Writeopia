import CoreGraphics
import Foundation
import ImageIO

/// A decoded picture of an equation, handed between tasks. `CGImage` is immutable.
nonisolated struct EquationPicture: @unchecked Sendable {
    let image: CGImage
}

/// Loads the pictures of the equations once per address. The backend never changes a picture
/// once written (the name is the hash of the LaTeX), so the cache is never stale. `data:` URLs
/// (the backend in debug mode) decode without a request.
final class EquationPictureLoader {
    static let shared = EquationPictureLoader()

    private let cache = NSCache<NSURL, EquationPictureBox>()
    private var inFlight: [URL: Task<EquationPicture?, Never>] = [:]
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
        cache.countLimit = 200
    }

    func picture(at url: URL) async -> CGImage? {
        if let cached = cache.object(forKey: url as NSURL) { return cached.picture.image }
        if let running = inFlight[url] { return await running.value?.image }

        let session = self.session
        let task = Task<EquationPicture?, Never> {
            guard let (data, _) = try? await session.data(from: url) else { return nil }
            return await Task.detached(priority: .userInitiated) { Self.decode(data) }.value
        }
        inFlight[url] = task
        let picture = await task.value
        inFlight[url] = nil
        if let picture { cache.setObject(EquationPictureBox(picture), forKey: url as NSURL) }
        return picture?.image
    }

    nonisolated private static func decode(_ data: Data) -> EquationPicture? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else { return nil }
        return EquationPicture(image: image)
    }
}

private final class EquationPictureBox {
    let picture: EquationPicture
    init(_ picture: EquationPicture) { self.picture = picture }
}
