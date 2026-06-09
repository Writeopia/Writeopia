package io.writeopia.ui.drawer.decorations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.model.DrawConfig

object DefaultTagDecoration : TagDecoration {

    private val CORNER_RADIUS = 8.dp

    // Extension to cover gaps between drawers
    private val BORDER_EXTENSION = 16.dp

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
                val accentLineColor = config.cardAccentLineColor()
                // Use graphicsLayer with clip=false to allow borders to extend beyond bounds
                modifier
                    .graphicsLayer(clip = false)
                    .background(config.cardHighlightBackgroundColor(), shape)
                    .cardBorder(
                        position = position,
                        color = borderColor,
                        strokeWidth = 1.dp,
                        cornerRadius = CORNER_RADIUS,
                        extension = BORDER_EXTENSION,
                        accentLineColor = accentLineColor,
                        accentLineWidth = 3.dp
                    )
                    .padding(paddingForCard(tags, config))
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
     * - Position -1 (TOP): top, left, right borders with rounded top corners, extended down
     * - Position 0 (MIDDLE): left, right borders extended both up and down to cover gaps
     * - Position 1 (BOTTOM): bottom, left, right borders with rounded bottom corners, extended up
     * - Position 2 (STANDALONE): all 4 borders with all corners rounded
     *
     * Also draws an accent line on the left side of the card.
     */
    private fun Modifier.cardBorder(
        position: Int,
        color: Color,
        strokeWidth: Dp,
        cornerRadius: Dp,
        extension: Dp,
        accentLineColor: Color,
        accentLineWidth: Dp
    ): Modifier = this.drawWithCache {
        val stroke = strokeWidth.toPx()
        val halfStroke = stroke / 2
        val radius = cornerRadius.toPx()
        val ext = extension.toPx()
        val accentWidth = accentLineWidth.toPx()
        val accentOffset = stroke + 18.dp.toPx()
        val cornerMargin = 12.dp.toPx()

        onDrawBehind {
            when (position) {
                -1 -> {
                    // TOP: rounded top corners, extend borders down past bottom to cover gap
                    val path = Path().apply {
                        // Start below bottom-left (extended)
                        moveTo(halfStroke, size.height + ext)
                        // Line up to top-left corner
                        lineTo(halfStroke, radius + halfStroke)
                        // Top-left rounded corner
                        quadraticTo(halfStroke, halfStroke, radius + halfStroke, halfStroke)
                        // Top edge
                        lineTo(size.width - radius - halfStroke, halfStroke)
                        // Top-right rounded corner
                        quadraticTo(
                            size.width - halfStroke,
                            halfStroke,
                            size.width - halfStroke,
                            radius + halfStroke
                        )
                        // Line down below bottom-right (extended)
                        lineTo(size.width - halfStroke, size.height + ext)
                    }
                    drawPath(path, color, style = Stroke(width = stroke))

                    // Accent line for TOP: starts after the rounded corner with margin, extends down
                    drawLine(
                        color = accentLineColor,
                        start = Offset(accentOffset, radius + halfStroke + cornerMargin),
                        end = Offset(accentOffset, size.height + ext),
                        strokeWidth = accentWidth,
                        cap = StrokeCap.Round
                    )
                }

                0 -> {
                    // MIDDLE: left and right borders extended both up and down to cover gaps
                    // Left border - extended
                    drawLine(
                        color = color,
                        start = Offset(halfStroke, -ext),
                        end = Offset(halfStroke, size.height + ext),
                        strokeWidth = stroke
                    )
                    // Right border - extended
                    drawLine(
                        color = color,
                        start = Offset(size.width - halfStroke, -ext),
                        end = Offset(size.width - halfStroke, size.height + ext),
                        strokeWidth = stroke
                    )

                    // Accent line for MIDDLE: full height extended (no rounded caps needed)
                    drawLine(
                        color = accentLineColor,
                        start = Offset(accentOffset, -ext),
                        end = Offset(accentOffset, size.height + ext),
                        strokeWidth = accentWidth
                    )
                }

                1 -> {
                    // BOTTOM: rounded bottom corners, extend borders up past top to cover gap
                    val path = Path().apply {
                        // Start above top-left (extended)
                        moveTo(halfStroke, -ext)
                        // Line down to bottom-left corner
                        lineTo(halfStroke, size.height - radius - halfStroke)
                        // Bottom-left rounded corner
                        quadraticTo(
                            halfStroke,
                            size.height - halfStroke,
                            radius + halfStroke,
                            size.height - halfStroke
                        )
                        // Bottom edge
                        lineTo(size.width - radius - halfStroke, size.height - halfStroke)
                        // Bottom-right rounded corner
                        quadraticTo(
                            size.width - halfStroke,
                            size.height - halfStroke,
                            size.width - halfStroke,
                            size.height - radius - halfStroke
                        )
                        // Line up above top-right (extended)
                        lineTo(size.width - halfStroke, -ext)
                    }
                    drawPath(path, color, style = Stroke(width = stroke))

                    // Accent line for BOTTOM: starts from top extended, stops before rounded corner
                    drawLine(
                        color = accentLineColor,
                        start = Offset(accentOffset, -ext),
                        end = Offset(accentOffset, size.height - radius - halfStroke - cornerMargin),
                        strokeWidth = accentWidth,
                        cap = StrokeCap.Round
                    )
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

                    // Accent line for STANDALONE: between rounded corners with margin and rounded caps
                    drawLine(
                        color = accentLineColor,
                        start = Offset(accentOffset, radius + halfStroke + cornerMargin),
                        end = Offset(accentOffset, size.height - radius - halfStroke - cornerMargin),
                        strokeWidth = accentWidth,
                        cap = StrokeCap.Round
                    )
                }
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

    /**
     * Padding for card content - includes extra space for the accent bar on the left
     */
    private fun paddingForCard(
        tagInfoList: Iterable<TagInfo>,
        drawConfig: DrawConfig
    ): PaddingValues {
        val padding = 8.dp
        // Extra padding for the accent bar: 14dp offset + 3dp width + 4dp margin = ~21dp extra
        val startPadding = drawConfig.textDrawerInnerStartPadding + 18

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
