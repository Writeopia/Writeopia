package io.writeopia.ui.drawer.decorations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.model.DrawConfig

interface TagDecoration {

    @Composable
    fun decorate(modifier: Modifier, tags: Iterable<TagInfo>, config: DrawConfig): Modifier

    @Composable
    fun background(defaultColor: Color, tags: Iterable<Tag>, config: DrawConfig): Color
}
