package io.writeopia.ui.drawer.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.model.DrawInfo

class ImagePreviewDrawer(
    private val modifier: Modifier = Modifier,
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(step.url ?: step.path)
                .build(),
            contentScale = ContentScale.Crop,
            contentDescription = "",
            modifier = modifier
                .padding(top = 30.dp)
                .clip(
                    shape = RoundedCornerShape(size = 12.dp)
                ).fillMaxSize()
                .heightIn(max = 200.dp),
            loading = {
                CircularProgressIndicator()
            },
        )
    }
}
