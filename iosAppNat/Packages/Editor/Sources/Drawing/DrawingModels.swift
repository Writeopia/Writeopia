import Foundation

/// Tools of the drawing editor. Mirrors `DrawingTool` of the Kotlin models.
nonisolated public enum DrawingTool: String, Codable, CaseIterable, Sendable {
    case pen = "PEN"
    case highlighter = "HIGHLIGHTER"
    case eraser = "ERASER"
}

nonisolated public struct DrawPoint: Codable, Equatable, Sendable {
    public var x: Double
    public var y: Double
    public var pressure: Double
    public var timestamp: Int64

    public init(x: Double, y: Double, pressure: Double = 1, timestamp: Int64 = 0) {
        self.x = x
        self.y = y
        self.pressure = pressure
        self.timestamp = timestamp
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        x = try container.decode(Double.self, forKey: .x)
        y = try container.decode(Double.self, forKey: .y)
        pressure = try container.decodeIfPresent(Double.self, forKey: .pressure) ?? 1
        timestamp = try container.decodeIfPresent(Int64.self, forKey: .timestamp) ?? 0
    }
}

nonisolated public struct Stroke: Codable, Equatable, Identifiable, Sendable {
    /// Opaque black as a signed ARGB integer, the default of the Kotlin models.
    public static let black = DrawingColor.argb(0xFF00_0000)

    public var id: String
    public var points: [DrawPoint]
    /// Signed 32-bit ARGB, like a Compose `Color.toArgb()`.
    public var color: Int
    public var strokeWidth: Double
    public var tool: DrawingTool

    public init(
        id: String = UUID().uuidString,
        points: [DrawPoint] = [],
        color: Int = Stroke.black,
        strokeWidth: Double = 4,
        tool: DrawingTool = .pen
    ) {
        self.id = id
        self.points = points
        self.color = color
        self.strokeWidth = strokeWidth
        self.tool = tool
    }

    // The Kotlin serializer skips default values, so every field but the points may be missing.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? UUID().uuidString
        points = try container.decodeIfPresent([DrawPoint].self, forKey: .points) ?? []
        color = try container.decodeIfPresent(Int.self, forKey: .color) ?? Stroke.black
        strokeWidth = try container.decodeIfPresent(Double.self, forKey: .strokeWidth) ?? 4
        tool = try container.decodeIfPresent(DrawingTool.self, forKey: .tool) ?? .pen
    }
}

/// A drawing, stored as JSON in the text of a `DRAWING` step. Mirrors `DrawingData` of the
/// Kotlin models, so drawings made on Android and iOS open on both.
nonisolated public struct DrawingData: Codable, Equatable, Sendable {
    public var id: String
    public var strokes: [Stroke]
    public var width: Int
    public var height: Int
    public var backgroundColor: Int?

    public init(id: String = UUID().uuidString, strokes: [Stroke] = [], width: Int = 0, height: Int = 0, backgroundColor: Int? = nil) {
        self.id = id
        self.strokes = strokes
        self.width = width
        self.height = height
        self.backgroundColor = backgroundColor
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? UUID().uuidString
        strokes = try container.decodeIfPresent([Stroke].self, forKey: .strokes) ?? []
        width = try container.decodeIfPresent(Int.self, forKey: .width) ?? 0
        height = try container.decodeIfPresent(Int.self, forKey: .height) ?? 0
        backgroundColor = try container.decodeIfPresent(Int.self, forKey: .backgroundColor)
    }

    public static func fromJson(_ json: String?) -> DrawingData? {
        guard let json, let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(DrawingData.self, from: data)
    }

    public func toJson() -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return (try? encoder.encode(self)).map { String(decoding: $0, as: UTF8.self) } ?? "{}"
    }

    public var isEmpty: Bool { strokes.allSatisfy { $0.points.isEmpty } }

    /// Area covered by the strokes plus `padding` around it, like the preview of the SDK.
    public func bounds(padding: Double = 40) -> CGRect? {
        let points = strokes.flatMap(\.points)
        guard let first = points.first else { return nil }

        var minX = first.x, maxX = first.x, minY = first.y, maxY = first.y
        for point in points {
            minX = min(minX, point.x)
            maxX = max(maxX, point.x)
            minY = min(minY, point.y)
            maxY = max(maxY, point.y)
        }
        return CGRect(x: minX - padding, y: minY - padding, width: maxX - minX + padding * 2, height: maxY - minY + padding * 2)
    }
}

/// Converts between the signed ARGB integers of the Kotlin models and color components.
nonisolated public enum DrawingColor {
    /// Signed ARGB integer from a hex literal like `0xFFFF0000`.
    public static func argb(_ hex: UInt32) -> Int {
        Int(Int32(bitPattern: hex))
    }

    public static func components(_ color: Int) -> (red: Double, green: Double, blue: Double, alpha: Double) {
        let value = UInt32(truncatingIfNeeded: color)
        return (
            Double((value >> 16) & 0xFF) / 255,
            Double((value >> 8) & 0xFF) / 255,
            Double(value & 0xFF) / 255,
            Double((value >> 24) & 0xFF) / 255
        )
    }

    /// The preset colors of the Compose drawing toolbar.
    public static let presets: [Int] = [
        0xFF00_0000, 0xFFFF_FFFF, 0xFFFF_0000, 0xFF00_FF00, 0xFF00_00FF, 0xFFFF_FF00,
        0xFFFF_00FF, 0xFF00_FFFF, 0xFFFF_8000, 0xFF80_00FF, 0xFF80_8080,
    ].map(argb)

    /// The preset stroke widths of the Compose drawing toolbar.
    public static let strokeWidths: [Double] = [4, 8, 12, 16, 20]
}
