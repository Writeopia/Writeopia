import Foundation

/// The AI commands of the editor answered by a model running in Ollama or llmman.
public final class OllamaAi: AiStreaming {
    public let api: OllamaAPI
    public let model: String

    public init(api: OllamaAPI, model: String) {
        self.api = api
        self.model = model
    }

    public func stream(_ command: AiCommand, prompt: String) -> AsyncThrowingStream<String, Error> {
        api.generate(model: model, system: AiPrompts.instructions, prompt: AiPrompts.prompt(for: command, text: prompt))
    }
}
