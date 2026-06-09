package io.writeopia.ui.drawer.decorations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.model.DrawConfig

object DefaultTagDecoration : TagDecoration {

    private val CORNER_RADIUS = 8.dp

    @Composable
    override fun decorate(
        modifier: Modifier,
        tags: Iterable<TagInfo>,
        config: DrawConfig
    ): Modifier {
        val tagSet = tags.mapTo(mutableSetOf()) { it.tag }

        return when {
            tagSet.contains(Tag.CARD_BLOCK) -> {
                val shape = shapeForTagInfo(tags)
                val position = getPositionFromTags(tags)
                val borderColor = config.cardHighlightBorderColor()
                modifier
                    .background(config.cardHighlightBackgroundColor(), shape)
                    .cardBorder(position, borderColor, 1.dp, CORNER_RADIUS)
                    .padding(paddingForTagInfo(tags, config))
            }
            tagSet.contains(Tag.HIGH_LIGHT_BLOCK) -> {
                modifier
                    .background(config.selectedColor(), shapeForTagInfo(tags))
                    .padding(paddingForTagInfo(tags, config))
            }
            else -> {
                modifier.padding(start = config.textDrawerInnerStartPadding.dp)
            }
        }
    }

    @Composable
    override fun background(defaultColor: Color, tags: Iterable<Tag>, config: DrawConfig): Color =
        when {
            tags.contains(Tag.CARD_BLOCK) -> config.cardHighlightBackgroundColor()
            tags.contains(Tag.HIGH_LIGHT_BLOCK) -> config.selectedColor()
            else -> defaultColor
        }

    private fun getPositionFromTags(tagInfoList: Iterable<TagInfo>): Int {
        val tagInfo = tagInfoList.firstOrNull { info ->
            info.tag.hasPosition()
        } ?: return 2 // Default to standalone (all borders)
        return tagInfo.position
    }

    /**
     * Draw card borders based on position with rounded corners:
     * - Position -1 (TOP): top, left, right borders with rounded top corners
     * - Position 0 (MIDDLE): left, right borders only (no rounded corners)
     * - Position 1 (BOTTOM): bottom, left, right borders with rounded bottom corners
     * - Position 2 (STANDALONE): all 4 borders with all corners rounded
     */
    private fun Modifier.cardBorder(
        position: Int,
        color: Color,
        strokeWidth: Dp,
        cornerRadius: Dp
    ): Modifier = this.drawBehind {
        val stroke = strokeWidth.toPx()
        val halfStroke = stroke / 2
        val radius = cornerRadius.toPx()

        when (position) {
            -1 -> {
                // TOP: rounded top corners, open at bottom
                val path = Path().apply {
                    // Start at bottom-left
                    moveTo(halfStroke, size.height)
                    // Line up to top-left corner
                    lineTo(halfStroke, radius + halfStroke)
                    // Top-left rounded corner
                    quadraticTo(halfStroke, halfStroke, radius + halfStroke, halfStroke)
                    // Top edge
                    lineTo(size.width - radius - halfStroke, halfStroke)
                    // Top-right rounded corner
                    quadraticTo(size.width - halfStroke, halfStroke, size.width - halfStroke, radius + halfStroke)
                    // Line down to bottom-right
                    lineTo(size.width - halfStroke, size.height)
                }
                drawPath(path, color, style = Stroke(width = stroke))
            }
            0 -> {
                // MIDDLE: only left and right borders, no corners
                // Left border
                drawLine(
                    color = color,
                    start = Offset(halfStroke, 0f),
                    end = Offset(halfStroke, size.height),
                    strokeWidth = stroke
                )
                // Right border
                drawLine(
                    color = color,
                    start = Offset(size.width - halfStroke, 0f),
                    end = Offset(size.width - halfStroke, size.height),
                    strokeWidth = stroke
                )
            }
            1 -> {
                // BOTTOM: rounded bottom corners, open at top
                val path = Path().apply {
                    // Start at top-left
                    moveTo(halfStroke, 0f)
                    // Line down to bottom-left corner
                    lineTo(halfStroke, size.height - radius - halfStroke)
                    // Bottom-left rounded corner
                    quadraticTo(halfStroke, size.height - halfStroke, radius + halfStroke, size.height - halfStroke)
                    // Bottom edge
                    lineTo(size.width - radius - halfStroke, size.height - halfStroke)
                    // Bottom-right rounded corner
                    quadraticTo(size.width - halfStroke, size.height - halfStroke, size.width - halfStroke, size.height - radius - halfStroke)
                    // Line up to top-right
                    lineTo(size.width - halfStroke, 0f)
                }
                drawPath(path, color, style = Stroke(width = stroke))
            }
            2 -> {
                // STANDALONE: all 4 borders with rounded corners
                drawRoundRect(
                    color = color,
                    topLeft = Offset(halfStroke, halfStroke),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = stroke)
                )
            }
        }
    }

    private fun shapeForTagInfo(tagInfoList: Iterable<TagInfo>): Shape {
        val corner = CORNER_RADIUS

        val tagInfo = tagInfoList.firstOrNull { info ->
            info.tag.hasPosition()
        } ?: return RoundedCornerShape(corner)

        return when (tagInfo.position) {
            -1 -> RoundedCornerShape(topStart = corner, topEnd = corner)
            1 -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
            2 -> RoundedCornerShape(corner)
            else -> RoundedCornerShape(0)
        }
    }

    private fun paddingForTagInfo(
        tagInfoList: Iterable<TagInfo>,
        drawConfig: DrawConfig
    ): PaddingValues {
        val padding = 8.dp
        val startPadding = drawConfig.textDrawerInnerStartPadding

        val tagInfo = tagInfoList.firstOrNull { info ->
            info.tag.hasPosition()
        } ?: return PaddingValues(0.dp)

        return when (tagInfo.position) {
            -1 -> PaddingValues(start = startPadding.dp, top = padding)
            1 -> PaddingValues(start = startPadding.dp, bottom = padding)
            2 -> PaddingValues(start = startPadding.dp, top = padding, bottom = padding)
            else -> PaddingValues(start = startPadding.dp)
        }
    }
}
