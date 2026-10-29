package io.writeopia.ui.image

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size as RequestSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The picture of a header, scaled to cover the whole space it is given with its proportions kept.
 * The drawing of the picture is what covers the space: the blurred bands the image model paints
 * above and below it ([HeaderPictureCrop]) are cropped away, like whatever else spills over.
 * Nothing is drawn until the picture is loaded and measured, so it never jumps.
 */
@Composable
fun HeaderImage(url: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val request = ImageRequest.Builder(LocalPlatformContext.current)
        .data(url)
        .size(RequestSize.ORIGINAL)
        .softwarePixels()
        .build()
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    val picture = (state as? AsyncImagePainter.State.Success)?.painter
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var strip by remember(url) { mutableStateOf(HeaderStripCache[url]) }

    LaunchedEffect(picture) {
        if (picture == null || strip != null) return@LaunchedEffect
        val bitmap = picture.rasterize(density, layoutDirection) ?: return@LaunchedEffect
        val rows = withContext(Dispatchers.Default) { HeaderPictureCrop.contentRows(bitmap) }
        HeaderStripCache[url] = rows
        strip = rows
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .semantics {
                role = Role.Image
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .drawBehind {
                val rows = strip ?: return@drawBehind
                if (picture == null || !picture.intrinsicSize.isUsable()) return@drawBehind
                val placement = HeaderPictureCrop.coverPlacement(picture.intrinsicSize, rows, size)
                withTransform({
                    translate(placement.offset.x, placement.offset.y)
                    scale(placement.scale, placement.scale, pivot = Offset.Zero)
                }) {
                    with(picture) { draw(intrinsicSize) }
                }
            }
    )
}

/** The rows of the drawing of every picture measured so far, so a list of cards measures each once. */
private object HeaderStripCache {
    private const val LIMIT = 200
    private val strips = HashMap<String, IntRange>()

    operator fun get(url: String): IntRange? = strips[url]

    operator fun set(url: String, rows: IntRange) {
        if (strips.size >= LIMIT) strips.clear()
        strips[url] = rows
    }
}

private fun Size.isUsable(): Boolean = isSpecified && width >= 1f && height >= 1f

/** The picture as a bitmap whose pixels can be read, drawn at its own size. */
private fun Painter.rasterize(density: Density, layoutDirection: LayoutDirection): ImageBitmap? {
    if (!intrinsicSize.isUsable()) return null
    val width = intrinsicSize.width.roundToInt()
    val height = intrinsicSize.height.roundToInt()
    val bitmap = ImageBitmap(width, height)
    val painter = this
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        with(painter) { draw(size) }
    }
    return bitmap
}
