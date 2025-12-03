package io.writeopia.notemenu.ui.screen.configuration.molecules

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.SlideInBox
import io.writeopia.ui.icons.WrSdkIcons
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun NotesSelectionMenu(
    modifier: Modifier = Modifier,
    visibilityState: Boolean,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onFavorite: () -> Unit,
    onSummary: () -> Unit,
    onClose: () -> Unit,
    shape: Shape = RoundedCornerShape(
        CornerSize(16.dp),
        CornerSize(16.dp),
        CornerSize(0.dp),
        CornerSize(0.dp)
    ),
    exitAnimationOffset: Float = 1F,
    enterAnimationSpec: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.6F,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = IntOffset.VisibilityThreshold
    ),
    exitAnimationSpec: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )
) {
    SlideInBox(
        modifier = modifier,
        editState = visibilityState,
        showBackground = false,
        outsideClick = { },
        enterAnimationSpec = enterAnimationSpec,
        exitAnimationSpec = exitAnimationSpec,
        animationLabel = "configurationsMenuAnimation",
        extraExitOffset = exitAnimationOffset
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            Row {
                val tintColor = MaterialTheme.colorScheme.onPrimary

                Icon(
                    modifier = Modifier
                        .clickable(onClick = onCopy)
                        .weight(1F)
                        .padding(vertical = 25.dp),
                    imageVector = WrSdkIcons.copy,
                    contentDescription = "Copy note",
//                    stringResource(R.string.copy_note),
                    tint = tintColor
                )

                Icon(
                    modifier = Modifier
                        .clickable(onClick = onFavorite)
                        .weight(1F)
                        .padding(vertical = 25.dp),
                    imageVector = WrIcons.favorites,
                    contentDescription = "favorite",
//                    stringResource(R.string.favorite),
                    tint = tintColor
                )

                Icon(
                    modifier = Modifier
                        .clickable(onClick = onSummary)
                        .weight(1F)
                        .padding(vertical = 25.dp),
                    imageVector = WrSdkIcons.ai,
                    contentDescription = "AI Summary",
                    tint = tintColor
                )

                Icon(
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .weight(1F)
                        .padding(vertical = 25.dp),
                    imageVector = WrIcons.delete,
                    contentDescription = "Delete",
//                    stringResource(R.string.delete),
                    tint = tintColor
                )

                Icon(
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .weight(1F)
                        .padding(vertical = 25.dp),
                    imageVector = WrIcons.close,
                    contentDescription = "Close",
//                    stringResource(R.string.delete),
                    tint = tintColor
                )
            }
        }
    }
}

@Preview
@Composable
internal fun NotesSelectionMenuPreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        NotesSelectionMenu(
            visibilityState = true,
            onDelete = {},
            onCopy = {},
            onFavorite = {},
            onSummary = {},
            onClose = {}
        )
    }
}
