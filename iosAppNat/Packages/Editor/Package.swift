// swift-tools-version: 6.2

import PackageDescription

let mainActorByDefault: [SwiftSetting] = [.defaultIsolation(MainActor.self)]

/// The text editor, split like the Kotlin SDK:
/// - `Writeopia`: editing logic over the list of story steps (like the `writeopia` module).
/// - `WriteopiaUI`: state manager and the drawers of each step type (like `writeopia_ui`).
/// - `Drawing`: free drawing, stored in `DRAWING` steps (like the `drawing` feature).
/// - `NoteEditor`: the editor screen of the app (like the `editor` feature).
let package = Package(
    name: "Editor",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "Writeopia", targets: ["Writeopia"]),
        .library(name: "WriteopiaUI", targets: ["WriteopiaUI"]),
        .library(name: "Drawing", targets: ["Drawing"]),
        .library(name: "NoteEditor", targets: ["NoteEditor"]),
    ],
    dependencies: [
        .package(path: "../Core"),
    ],
    targets: [
        .target(
            name: "Writeopia",
            dependencies: [.product(name: "WrModels", package: "Core")]
        ),
        .target(
            name: "WriteopiaUI",
            dependencies: [
                "Writeopia",
                .product(name: "WrModels", package: "Core"),
                .product(name: "WrDesign", package: "Core"),
            ],
            swiftSettings: mainActorByDefault
        ),
        .target(
            name: "Drawing",
            dependencies: [
                .product(name: "WrModels", package: "Core"),
                .product(name: "WrDesign", package: "Core"),
            ],
            swiftSettings: mainActorByDefault
        ),
        .target(
            name: "NoteEditor",
            dependencies: [
                "Writeopia",
                "WriteopiaUI",
                "Drawing",
                .product(name: "WrModels", package: "Core"),
                .product(name: "WrData", package: "Core"),
                .product(name: "WrNetwork", package: "Core"),
                .product(name: "WrDesign", package: "Core"),
            ],
            swiftSettings: mainActorByDefault
        ),
        .testTarget(
            name: "WriteopiaTests",
            dependencies: ["Writeopia", .product(name: "WrModels", package: "Core")]
        ),
        .testTarget(
            name: "DrawingTests",
            dependencies: ["Drawing"],
            swiftSettings: mainActorByDefault
        ),
        .testTarget(
            name: "NoteEditorTests",
            dependencies: [
                "NoteEditor", "WriteopiaUI", "Writeopia", "Drawing",
                .product(name: "WrModels", package: "Core"),
                .product(name: "WrData", package: "Core"),
                .product(name: "WrNetwork", package: "Core"),
            ],
            swiftSettings: mainActorByDefault
        ),
        .testTarget(
            name: "WriteopiaUITests",
            dependencies: ["WriteopiaUI", "Writeopia", .product(name: "WrModels", package: "Core")],
            swiftSettings: mainActorByDefault
        ),
    ]
)
