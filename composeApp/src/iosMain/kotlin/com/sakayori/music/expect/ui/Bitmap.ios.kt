package com.sakayori.music.expect.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import coil3.Image
import coil3.toBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkiaImage

actual fun ImageBitmap.toByteArray(): ByteArray? {
    val image = SkiaImage.makeFromBitmap(this.asSkiaBitmap())
    return image.encodeToData(EncodedImageFormat.JPEG, 100)?.bytes
}

actual fun Image.toImageBitmap(): ImageBitmap =
    this.toBitmap().asComposeImageBitmap()
