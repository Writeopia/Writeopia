package io.writeopia.editor.configuration.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.editor.features.editor.viewmodel.HeaderImageGeneration
import io.writeopia.editor.features.editor.viewmodel.HeaderImagesState
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme

/**
 * The options of the header: no header, a color, or a picture of the workspace. A picture can
 * also be drawn by the AI, shown here to be approved or drawn again.
 */
@Composable
fun HeaderEditionOptions(
    availableColors: List<Int>,
    selectedColor: Int?,
    onColorSelection: (Int?) -> Unit,
    headerImages: HeaderImagesState = HeaderImagesState(),
    selectedImageUrl: String? = null,
    onImageSelection: (String) -> Unit = {},
    onGenerateImage: () -> Unit = {},
    onApproveImage: () -> Unit = {},
    onDiscardImage: () -> Unit = {},
) {
    val colors = listOf(Color.Transparent.toArgb()) + availableColors

    Text(
        modifier = Modifier.padding(horizontal = 16.dp),
        text = WrStrings.headerTitle(),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(12.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(colors) { i, color ->
            val isSelected = if (i == 0) {
                selectedColor == null && selectedImageUrl == null
            } else {
                selectedImageUrl == null && color == selectedColor
            }

            val shape = RoundedCornerShape(animatedCorner(isSelected))

            Box(
                modifier = Modifier
                    .clip(shape)
                    .clickable {
                        onColorSelection(if (i == 0) null else color)
                    }
                    .border(
                        BorderStroke(1.dp, WriteopiaTheme.colorScheme.dividerColor),
                        shape = shape
                    )
                    .height(40.dp)
                    .width(animatedWidth(isSelected, 40.dp, 50.dp))
                    .background(Color(color))
            ) {
                if (i == 0) {
                    Icon(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp),
                        imageVector = WrIcons.transparent,
                        contentDescription = "Remove color",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }

    val showsImages = headerImages.canGenerate ||
        headerImages.images.isNotEmpty() ||
        headerImages.generation != HeaderImageGeneration.Idle

    if (showsImages) {
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = WrStrings.headerImages(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyVerticalGrid(
            modifier = Modifier.fillMaxWidth().heightIn(max = imagesGridMaxHeight),
            columns = GridCells.Adaptive(minSize = tileWidth),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            if (headerImages.canGenerate) {
                item {
                    GenerateImageTile(
                        enabled = headerImages.generation !is HeaderImageGeneration.Generating,
                        onClick = onGenerateImage
                    )
                }
            }

            items(headerImages.images, key = { url -> url }) { url ->
                ImageTile(
                    url = url,
                    isSelected = url == selectedImageUrl,
                    onClick = { onImageSelection(url) }
                )
            }
        }

        AnimatedVisibility(headerImages.generation != HeaderImageGeneration.Idle) {
            GenerationPanel(
                generation = headerImages.generation,
                onApproveImage = onApproveImage,
                onGenerateImage = onGenerateImage,
                onDiscardImage = onDiscardImage,
            )
        }
    }
}

private val tileHeight = 56.dp
private val tileWidth = 100.dp

// About three rows of pictures; more pictures scroll inside the grid.
private val imagesGridMaxHeight = 200.dp

@Composable
private fun GenerateImageTile(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .border(BorderStroke(1.dp, WriteopiaTheme.colorScheme.dividerColor), shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .height(tileHeight)
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = WrIcons.ai,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = WrStrings.headerGenerateImage(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun ImageTile(url: String, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        WriteopiaTheme.colorScheme.dividerColor
    }

    AsyncImage(
        model = url,
        contentDescription = "Header image",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .border(BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor), shape)
            .height(tileHeight)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

/** The picture the AI is drawing or drew, with the choice of keeping it or drawing another. */
@Composable
private fun GenerationPanel(
    generation: HeaderImageGeneration,
    onApproveImage: () -> Unit,
    onGenerateImage: () -> Unit,
    onDiscardImage: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        val previewShape = RoundedCornerShape(12.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(previewShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when (generation) {
                is HeaderImageGeneration.Candidate -> {
                    AsyncImage(
                        model = generation.url,
                        contentDescription = "Generated header image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is HeaderImageGeneration.Failed -> {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = generation.message ?: WrStrings.headerImageFailed(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = WrStrings.headerGeneratingImage(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (generation is HeaderImageGeneration.Candidate && generation.saveFailed) {
            Text(
                text = generation.saveError ?: WrStrings.headerImageSaveFailed(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            text = WrStrings.headerImageCost(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDiscardImage) {
                Text(WrStrings.headerDiscard())
            }

            val isSaving = generation is HeaderImageGeneration.Candidate && generation.isSaving

            if (generation !is HeaderImageGeneration.Generating) {
                OutlinedButton(onClick = onGenerateImage, enabled = !isSaving) {
                    Text(WrStrings.headerTryAnother())
                }
            }

            if (generation is HeaderImageGeneration.Candidate) {
                Button(onClick = onApproveImage, enabled = !isSaving) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(WrStrings.headerUseImage())
                    }
                }
            }
        }
    }
}

@Composable
private fun animatedCorner(isSelected: Boolean): Dp {
    val corner by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 30.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cornerRadius"
    )
    return corner
}

@Composable
private fun animatedWidth(isSelected: Boolean, normal: Dp, selected: Dp): Dp {
    val width by animateDpAsState(
        targetValue = if (isSelected) selected else normal,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "boxWidth"
    )
    return width
}
