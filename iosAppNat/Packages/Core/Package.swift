// swift-tools-version: 6.2

import PackageDescription

let mainActorByDefault: [SwiftSetting] = [.defaultIsolation(MainActor.self)]

let package = Package(
    name: "Core",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "WrModels", targets: ["WrModels"]),
        .library(name: "WrStorage", targets: ["WrStorage"]),
        .library(name: "WrNetwork", targets: ["WrNetwork"]),
        .library(name: "WrData", targets: ["WrData"]),
        .library(name: "WrSession", targets: ["WrSession"]),
        .library(name: "WrDesign", targets: ["WrDesign"]),
    ],
    targets: [
        .target(name: "WrModels"),
        .target(name: "WrStorage", swiftSettings: mainActorByDefault),
        .target(name: "WrNetwork", dependencies: ["WrModels", "WrStorage"], swiftSettings: mainActorByDefault),
        .target(name: "WrData", dependencies: ["WrModels", "WrNetwork", "WrStorage"], swiftSettings: mainActorByDefault),
        .target(
            name: "WrSession",
            dependencies: ["WrModels", "WrNetwork", "WrStorage", "WrData"],
            swiftSettings: mainActorByDefault
        ),
        .target(name: "WrDesign", dependencies: ["WrModels"], swiftSettings: mainActorByDefault),
        .testTarget(name: "WrDataTests", dependencies: ["WrData", "WrModels", "WrNetwork", "WrStorage"], swiftSettings: mainActorByDefault),
        .testTarget(name: "WrNetworkTests", dependencies: ["WrNetwork", "WrModels", "WrStorage", "WrData"], swiftSettings: mainActorByDefault),
    ]
)
