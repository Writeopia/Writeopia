import CoreGraphics
import Foundation
import ImageIO

/// Finds the rows of a header picture that hold the drawing. The image model paints a wide scene
/// across the middle of the frame and fills the rest with blurred bands, whose height changes
/// from one picture to the next, so the band of the header shows the drawing and never those
/// bands. The same rules as `HeaderPictureCrop` of the SDK.
nonisolated public enum HeaderPictureCrop {
    /// The picture is sampled on a grid of about this many rows and columns, whatever its size.
    private static let sampledRows = 384
    private static let sampledColumns = 336

    /// A row holds drawing when it is sharper than this share of the sharp rows of the picture.
    private static let shareOfSharp = 0.3

    /// Below this sharpness the whole picture is flat (a gradient, a plain color): no bands.
    private static let flat = 0.5

    /// A strip shorter than this share of the picture is a wrong guess: the whole picture is used.
    private static let minStrip = 0.2

    /// The rows of the drawing, read from the brightness of the pixels: `luma(x, y)` gives 0...255
    /// for a pixel, row 0 at the top. Blurred bands change little from one pixel to the next, the
    /// drawing changes a lot.
    public static func contentRows(width: Int, height: Int, luma: (Int, Int) -> Int) -> Range<Int> {
        let whole = 0..<height
        guard width >= 2, height >= 2 else { return whole }

        let rowStep = max(1, height / sampledRows)
        let columnStep = max(1, width / sampledColumns)
        let rows = Array(stride(from: 0, to: height, by: rowStep))
        let sharpness: [Double] = rows.map { y in
            var sum = 0
            var count = 0
            var x = columnStep
            while x < width {
                sum += abs(luma(x, y) - luma(x - columnStep, y))
                count += 1
                x += columnStep
            }
            return count == 0 ? 0 : Double(sum) / Double(count)
        }

        let sorted = sharpness.sorted()
        let reference = sorted[(sorted.count - 1) * 9 / 10]
        guard reference >= flat else { return whole }

        let threshold = reference * shareOfSharp
        guard let first = sharpness.firstIndex(where: { $0 > threshold }),
              let last = sharpness.lastIndex(where: { $0 > threshold }) else { return whole }

        let top = rows[first]
        let bottom = min(height, rows[last] + rowStep)
        guard Double(bottom - top) >= Double(height) * minStrip else { return whole }

        return top..<bottom
    }

    /// The rows of the drawing of `image`; reads every pixel, so call it off the main thread.
    public static func contentRows(of image: CGImage) -> Range<Int> {
        let width = image.width
        let height = image.height
        guard width >= 2, height >= 2 else { return 0..<height }

        var gray = [UInt8](repeating: 0, count: width * height)
        let drawn = gray.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width,
                space: CGColorSpaceCreateDeviceGray(),
                bitmapInfo: CGImageAlphaInfo.none.rawValue
            ) else { return false }
            // A bitmap context keeps its first row at the top of the picture.
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return 0..<height }

        return contentRows(width: width, height: height) { x, y in Int(gray[y * width + x]) }
    }

    /// The picture cut down to its drawing.
    public static func cropped(_ image: CGImage) -> CGImage {
        let rows = contentRows(of: image)
        guard rows.count < image.height,
              let cut = image.cropping(to: CGRect(x: 0, y: rows.lowerBound, width: image.width, height: rows.count))
        else { return image }
        return cut
    }
}
