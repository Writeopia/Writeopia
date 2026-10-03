package io.writeopia.ai

/** The AI commands of the editor. Each provider answers them with its own endpoint. */
enum class AiCommand {
    PROMPT,
    SUMMARY,
    ACTION_POINTS,
    FAQ,
    TAGS,
}
