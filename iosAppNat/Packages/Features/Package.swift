// swift-tools-version: 6.2

import PackageDescription

let mainActorByDefault: [SwiftSetting] = [.defaultIsolation(MainActor.self)]

let coreProducts: [Target.Dependency] = [
    .product(name: "WrModels", package: "Core"),
    .product(name: "WrNetwork", package: "Core"),
    .product(name: "WrStorage", package: "Core"),
    .product(name: "WrData", package: "Core"),
    .product(name: "WrSession", package: "Core"),
    .product(name: "WrDesign", package: "Core"),
]

let package = Package(
    name: "Features",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "AuthFeature", targets: ["AuthFeature"]),
        .library(name: "DocumentsFeature", targets: ["DocumentsFeature"]),
        .library(name: "SearchFeature", targets: ["SearchFeature"]),
        .library(name: "SettingsFeature", targets: ["SettingsFeature"]),
    ],
    dependencies: [
        .package(path: "../Core"),
    ],
    targets: [
        .target(name: "AuthFeature", dependencies: coreProducts, swiftSettings: mainActorByDefault),
        .target(name: "DocumentsFeature", dependencies: coreProducts, swiftSettings: mainActorByDefault),
        .target(name: "SearchFeature", dependencies: coreProducts + ["DocumentsFeature"], swiftSettings: mainActorByDefault),
        .target(name: "SettingsFeature", dependencies: coreProducts, swiftSettings: mainActorByDefault),
        .testTarget(
            name: "AuthFeatureTests",
            dependencies: ["AuthFeature"] + coreProducts,
            swiftSettings: mainActorByDefault
        ),
    ]
)
