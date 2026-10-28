import CoreGraphics
import Testing
@testable import WriteopiaUI

struct HeaderPictureCropTests {
    /// A drawing (pixels that differ from every neighbour) on the rows of `strip`, soft bands elsewhere.
    private func picture(strip: Range<Int>, band: @escaping (Int) -> Int = { _ in 128 }) -> (Int, Int) -> Int {
        { x, y in strip.contains(y) ? (x * 97 + y * 13) % 256 : band(y) }
    }

    @Test func bandsAboveAndBelowTheDrawingAreCutAway() {
        #expect(HeaderPictureCrop.contentRows(width: 200, height: 400, luma: picture(strip: 100..<300)) == 100..<300)
    }

    @Test func softGradientsCountAsBands() {
        let luma = picture(strip: 130..<310) { y in 40 + y / 4 }
        #expect(HeaderPictureCrop.contentRows(width: 200, height: 400, luma: luma) == 130..<310)
    }

    @Test func aBandOnOneSideOnlyIsCutAway() {
        #expect(HeaderPictureCrop.contentRows(width: 200, height: 400, luma: picture(strip: 0..<250)) == 0..<250)
    }

    @Test func aPictureWithoutBandsIsKeptWhole() {
        #expect(HeaderPictureCrop.contentRows(width: 200, height: 400, luma: picture(strip: 0..<400)) == 0..<400)
    }

    @Test func aFlatPictureIsKeptWhole() {
        #expect(HeaderPictureCrop.contentRows(width: 200, height: 400) { _, _ in 128 } == 0..<400)
    }

    @Test func aTinyStripIsNotTrusted() {
        #expect(HeaderPictureCrop.contentRows(width: 200, height: 400, luma: picture(strip: 190..<200)) == 0..<400)
    }

    @Test func bigPicturesAreSampledAndStillMeasured() {
        let rows = HeaderPictureCrop.contentRows(width: 1344, height: 768, luma: picture(strip: 184..<636))
        #expect(rows == 184..<636)
    }

    /// The rows of a `CGImage` are read top-down, so a strip near the top is cut near the top.
    @Test func imageRowsAreReadFromTheTop() throws {
        let width = 200, height = 400
        let luma = picture(strip: 50..<150)
        var gray = [UInt8](repeating: 0, count: width * height)
        for y in 0..<height { for x in 0..<width { gray[y * width + x] = UInt8(luma(x, y)) } }
        let image = try #require(gray.withUnsafeMutableBytes { buffer in
            CGContext(
                data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width,
                space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue
            )?.makeImage()
        })

        #expect(HeaderPictureCrop.contentRows(of: image) == 50..<150)
        let cropped = HeaderPictureCrop.cropped(image)
        #expect(cropped.width == 200)
        #expect(cropped.height == 100)
    }
}
