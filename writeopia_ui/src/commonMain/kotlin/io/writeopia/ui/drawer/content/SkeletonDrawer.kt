package io.writeopia.ui.drawer.content

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.model.DrawInfo

/**
 * The placeholder of text the AI is rewriting: pulsing bars, one per line of the text that was
 * there, so the document keeps about the same shape until the new text arrives.
 */
class SkeletonDrawer : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val lines = (step.text?.lineSequence()?.count() ?: 1).coerceIn(1, MAX_BARS)
        val transition = rememberInfiniteTransition()
        val alpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Column(modifier = Modifier.padding(start = 25.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
            repeat(lines) { index ->
                val widthFraction = if (index == lines - 1 && lines > 1) 0.6f else 0.95f

                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth(widthFraction)
                        .height(14.dp)
                        .alpha(alpha)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                )
            }
        }
    }

    companion object {
        private const val MAX_BARS = 8
    }
}
