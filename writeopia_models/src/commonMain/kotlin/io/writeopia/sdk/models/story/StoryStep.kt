package io.writeopia.sdk.models.story

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.link.DocumentLink
import io.writeopia.sdk.models.span.SpanInfo

/**
 * The model defining the information that can be draw in the screen. This is the most basic
 * building block of the library and can have many types like image, message, audio, video,
 * button, empty space, etc.
 *
 * @param id the ID of the story step. It should be used both in the database and backend.
 * @param localId This in a temporary ID that identifies the current drawing of the content
 * of the StoryStep. It should change everytime the StoryStep is going to be draw in the screen.
 * @param type [StoryType] The is the type that identifies the story step. The SDK supports many
 * different types like Message, Image, Check_Item, Space, Title, etc.
 * @param parentId The ID of a parent StoryStep, in case this StoryStep is inside a group.
 * @param url Used for fetch an image, video, audio or other type of content.
 * @param path Local path for content, like image, video, audio or other type of content.
 * @param text The text of the StoryStep.
 * @param checked If the type of this StorySteo is "check_item" or other type that can be checked
 * this parameter holds the information if it is checked or not.
 * @param steps List<StoryType> a groups of StorySteps that can be inside the current StoryStep, if
 * it is a group.
 * @param decoration [Decoration] The decoration fo the StoryStep.
 */
data class StoryStep(
    val id: String = GenerateId.generate(),
    val localId: String = GenerateId.generate(),
    val type: StoryType,
    val parentId: String? = null,
    val url: String? = null,
    val path: String? = null,
    val text: String? = null,
    val checked: Boolean? = false,
    val steps: List<StoryStep> = emptyList(),
    val tags: Set<TagInfo> = emptySet(),
    val spans: Set<SpanInfo> = emptySet(),
    val decoration: Decoration = Decoration(),
    val ephemeral: Boolean = false,
    val documentLink: DocumentLink? = null
) {

    val key: Int = localId.hashCode()

    val isGroup: Boolean = steps.isNotEmpty()

    fun copyNewLocalId(localId: String = GenerateId.generate()): StoryStep = copy(localId = localId)

    fun isTitle(): Boolean = when (StoryTypes.fromNumber(this.type.number)) {
        StoryTypes.TEXT -> Tag.titleTags()
            .map { TagInfo(it) }
            .any { titleTag -> tags.contains(titleTag) }

        StoryTypes.TITLE -> true

        else -> false
    }
}
