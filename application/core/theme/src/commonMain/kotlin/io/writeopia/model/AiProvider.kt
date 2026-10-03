package io.writeopia.model

/**
 * Who answers the AI commands, as picked by the user in the settings. Mirrors the provider choice
 * of the Mac app: the cloud AI of the Writeopia backend, or a model served on this machine.
 */
enum class AiProvider(val id: String) {
    /** The Writeopia backend (Gemini). Needs a session in an online workspace. */
    CLOUD("cloud"),

    /** A model served on this machine by Ollama. Works offline, in both spaces. */
    LOCAL("local");

    companion object {
        fun fromId(id: String?): AiProvider? = entries.find { provider -> provider.id == id }
    }
}
