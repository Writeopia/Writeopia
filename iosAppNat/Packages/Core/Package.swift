// swift-tools-version: 6.2

import PackageDescription

let mainActorByDefault: [SwiftSetting] = [.defaultIsolation(MainActor.self)]

let package = Package(
    name: "Core",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v26)],
    products: [
        .library(name: "WrModels", targets: ["WrModels"]),
        .library(name: "WrStorage", targets: ["WrStorage"]),
        .library(name: "WrNetwork", targets: ["WrNetwork"]),
        .library(name: "WrData", targets: ["WrData"]),
        .library(name: "WrSession", targets: ["WrSession"]),
        .library(name: "WrDesign", targets: ["WrDesign"]),
        .library(name: "WrAnalytics", targets: ["WrAnalytics"]),
    ],
    dependencies: [
        .package(url: "https://github.com/PostHog/posthog-ios", from: "3.0.0"),
    ],
    targets: [
        .target(name: "WrModels"),
        .target(name: "WrStorage", swiftSettings: mainActorByDefault),
        .target(name: "WrNetwork", dependencies: ["WrModels", "WrStorage"], swiftSettings: mainActorByDefault),
        .target(
            name: "WrData",
            dependencies: ["WrModels", "WrNetwork", "WrStorage"],
            resources: [.copy("Resources/Tutorials")],
            swiftSettings: mainActorByDefault
        ),
        .target(
            name: "WrAnalytics",
            dependencies: [.product(name: "PostHog", package: "posthog-ios")],
            swiftSettings: mainActorByDefault
        ),
        .target(
            name: "WrSession",
            dependencies: ["WrModels", "WrNetwork", "WrStorage", "WrData", "WrAnalytics"],
            swiftSettings: mainActorByDefault
        ),
        .target(name: "WrDesign", dependencies: ["WrModels"], swiftSettings: mainActorByDefault),
        .testTarget(name: "WrDataTests", dependencies: ["WrData", "WrModels", "WrNetwork", "WrStorage"], swiftSettings: mainActorByDefault),
        .testTarget(name: "WrNetworkTests", dependencies: ["WrNetwork", "WrModels", "WrStorage", "WrData"], swiftSettings: mainActorByDefault),
        .testTarget(name: "WrAnalyticsTests", dependencies: ["WrAnalytics"], swiftSettings: mainActorByDefault),
    ]
)
