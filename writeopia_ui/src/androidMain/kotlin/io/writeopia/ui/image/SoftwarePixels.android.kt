package io.writeopia.ui.image

import coil3.request.ImageRequest
import coil3.request.allowHardware

internal actual fun ImageRequest.Builder.softwarePixels(): ImageRequest.Builder = allowHardware(false)
