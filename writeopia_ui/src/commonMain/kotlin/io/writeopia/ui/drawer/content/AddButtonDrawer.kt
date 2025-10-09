package io.writeopia.ui.drawer.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Plus
import io.writeopia.ui.model.DrawInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.StoryStepDrawer

/**
 * A simple Add button.
 */
class AddButtonDrawer : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        Box(modifier = Modifier.padding(horizontal = 5.dp)) {
            Button(
                onClick = { },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(contentColor = Color.Blue),
                modifier = Modifier.size(45.dp),
                contentPadding = PaddingValues(1.dp)
            ) {
                Icon(imageVector = Lucide.Plus, contentDescription = "", tint = Color.White)
            }
        }
    }
}
