package io.writeopia.notemenu.ui.screen.configuration.molecules

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    onSummary: (() -> Unit)? = null,
    onClose: () -> Unit,
    onMove: (() -> Unit)? = null,
    showCopy: Boolean = true,
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
                .widthIn(max = 500.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tintColor = MaterialTheme.colorScheme.onPrimary

                // The actions scroll when they don't fit; close always stays at the end. When
                // they fit, they spread over the width like before.
                BoxWithConstraints(modifier = Modifier.weight(1F)) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .widthIn(min = maxWidth),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (showCopy) {
                            SelectionAction(WrSdkIcons.copy, "Copy note", tintColor, onCopy)
                        }

                        SelectionAction(WrIcons.favorites, "favorite", tintColor, onFavorite)

                        if (onSummary != null) {
                            SelectionAction(WrSdkIcons.ai, "AI Summary", tintColor, onSummary)
                        }

                        if (onMove != null) {
                            SelectionAction(WrIcons.moveToFolder, "Move to folder", tintColor, onMove)
                        }

                        SelectionAction(WrIcons.delete, "Delete", tintColor, onDelete)
                    }
                }

                SelectionAction(WrIcons.close, "Close", tintColor, onClose)
            }
        }
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Icon(
        modifier = Modifier
            .clickable(onClick = onClick)
            .widthIn(min = 60.dp)
            .padding(vertical = 25.dp),
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint
    )
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
