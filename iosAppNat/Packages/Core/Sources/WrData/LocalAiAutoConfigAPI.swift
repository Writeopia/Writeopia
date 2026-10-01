import Foundation
import WrNetwork

/// What the backend suggests for a local AI: where Ollama and llmman usually run and a model
/// per tier. Same JSON as `LocalAiAutoConfigResponse` of the Compose app.
public struct LocalAiAutoConfig: Codable, Sendable, Equatable {
    public enum Tier: String, Codable, Sendable, CaseIterable {
        case light = "LIGHT"
        case medium = "MEDIUM"
        case heavy = "HEAVY"
    }

    public struct ModelTier: Codable, Sendable, Equatable, Identifiable {
        public let type: Tier
        public let modelName: String

        public var id: String { "\(type.rawValue)-\(modelName)" }

        public init(type: Tier, modelName: String) {
            self.type = type
            self.modelName = modelName
        }
    }

    public let ollamaUrl: String
    public let llmmanUrl: String
    public let modelTiers: [ModelTier]
    public let defaultTierIndex: Int

    public init(ollamaUrl: String, llmmanUrl: String, modelTiers: [ModelTier], defaultTierIndex: Int) {
        self.ollamaUrl = ollamaUrl
        self.llmmanUrl = llmmanUrl
        self.modelTiers = modelTiers
        self.defaultTierIndex = defaultTierIndex
    }

    /// The defaults of the backend, used when it can't be reached (the private space is offline).
    public static let fallback = LocalAiAutoConfig(
        ollamaUrl: OllamaAPI.defaultURL.absoluteString,
        llmmanUrl: OllamaAPI.llmmanURL.absoluteString,
        modelTiers: [
            ModelTier(type: .light, modelName: "gemma4:e4b"),
            ModelTier(type: .medium, modelName: "gpt-oss:20b"),
            ModelTier(type: .heavy, modelName: "mistral-small:24b"),
        ],
        defaultTierIndex: 1
    )

    public var defaultTier: ModelTier? {
        modelTiers.indices.contains(defaultTierIndex) ? modelTiers[defaultTierIndex] : modelTiers.first
    }
}

public final class LocalAiAutoConfigAPI {
    private let client: APIClient

    public init(client: APIClient) {
        self.client = client
    }

    /// The suggestion of the backend, or the built-in defaults when it can't be reached or
    /// answers something unusable.
    public func config() async -> LocalAiAutoConfig {
        guard let config: LocalAiAutoConfig = try? await client.get("api/ai/local-config", authenticated: false),
              !config.modelTiers.isEmpty
        else { return .fallback }
        return config
    }
}
