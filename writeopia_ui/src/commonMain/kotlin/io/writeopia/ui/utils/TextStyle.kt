package io.writeopia.ui.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo

@Composable
fun defaultTextStyle(storyStep: StoryStep, fontFamily: FontFamily? = null): TextStyle {
    val isAiSuggestion = storyStep.tags.contains(TagInfo(Tag.AI_SUGGESTION))

    return TextStyle(
        textDecoration = if (storyStep.checked == true) TextDecoration.LineThrough else null,
        color = if (isAiSuggestion) Color.Gray else MaterialTheme.colorScheme.onBackground,
        fontSize = textSizeFromTags(storyStep.tags.map { it.tag }),
        fontFamily = if (isAiSuggestion) FontFamily.Monospace else fontFamily,
        fontStyle = FontStyle.Normal
    )
}

@Composable
fun previewTextStyle(storyStep: StoryStep, fontFamily: FontFamily? = null) =
    TextStyle(
        textDecoration = if (storyStep.checked == true) TextDecoration.LineThrough else null,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = textSizeFromTagsInPreview(storyStep.tags.map { it.tag }),
        fontFamily = fontFamily
    )

@Composable
fun codeBlockStyle() =
    TextStyle(
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )

private fun textSizeFromTags(tags: Iterable<Tag>) =
    tags.firstOrNull { tag -> tag.isTitle() }
        ?.let(::textSizeFromTag)
        ?: 16.sp

private fun textSizeFromTagsInPreview(tags: Iterable<Tag>) =
    tags.firstOrNull { tag -> tag.isTitle() }
        ?.let(::textSizeFromTagInPreview)
        ?: 12.sp

private fun textSizeFromTag(tags: Tag) =
    when (tags) {
        Tag.H1 -> 32.sp
        Tag.H2 -> 28.sp
        Tag.H3 -> 24.sp
        Tag.H4 -> 20.sp
        else -> 20.sp
    }

private fun textSizeFromTagInPreview(tags: Tag) =
    when (tags) {
        Tag.H1 -> 20.sp
        Tag.H2 -> 18.sp
        Tag.H3 -> 16.sp
        Tag.H4 -> 14.sp
        else -> 12.sp
    }
