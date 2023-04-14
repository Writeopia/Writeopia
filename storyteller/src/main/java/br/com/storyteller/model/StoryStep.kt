package br.com.storyteller.model

data class StoryStep(
    val id: String,
    val type: String,
    val url: String? = null,
    val path: String? = null,
    val text: String? = null,
    val localPosition: Int
) : Comparable<StoryStep> {
    override fun compareTo(other: StoryStep): Int = localPosition.compareTo(other.localPosition)

}

enum class StepType(val type: String) {
    MESSAGE("message"),
    IMAGE("image"),
    AUDIO("audio"),
    VIDEO("video"),
    ADD_BUTTON("add_button"),
}
