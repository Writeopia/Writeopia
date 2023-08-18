package com.github.leandroborgesferreira.storyteller.models.story

import java.util.UUID

/**
 * The model defining the information that can be draw in the screen. This is the most basic
 * building block of the library and can have many types like image, message, audio, video,
 * button, empty space, etc.
 */
data class StoryStep(
    val id: String = UUID.randomUUID().toString(),
    val localId: String = UUID.randomUUID().toString(),
    val type: StoryType,
    val parentId: String? = null,
    val url: String? = null,
    val path: String? = null,
    val text: String? = null,
    val title: String? = null,
    val checked: Boolean? = false,
    val steps: List<StoryStep> = emptyList(),
    val decoration: Decoration = Decoration()
) {

    val key: Int = localId.hashCode()

    val isGroup: Boolean = steps.isNotEmpty()

    fun copyNewLocalId(): StoryStep = copy(localId = UUID.randomUUID().toString())
}
