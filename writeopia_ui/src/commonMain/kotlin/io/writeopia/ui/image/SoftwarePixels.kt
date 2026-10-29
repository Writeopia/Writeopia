package io.writeopia.ui.image

import coil3.request.ImageRequest

/**
 * Asks for a picture whose pixels can be read. Only Android has a choice: its hardware bitmaps
 * can't be drawn into a bitmap in memory, which [HeaderImage] does to measure the picture.
 */
internal expect fun ImageRequest.Builder.softwarePixels(): ImageRequest.Builder
