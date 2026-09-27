import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Turns picked images (HEIC, PNG...) into JPEGs no larger than `maxDimension`, which every
/// platform of the app and the media service can read.
enum ImageProcessing {
    static let maxDimension: CGFloat = 2048

    static func jpeg(from data: Data, quality: CGFloat = 0.85) -> Data? {
        #if canImport(UIKit)
        guard let image = UIImage(data: data) else { return nil }

        let size = image.size
        let scale = min(1, maxDimension / max(size.width, size.height))
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: target, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
        return resized.jpegData(compressionQuality: quality)
        #else
        return nil
        #endif
    }
}
