package com.sakayori.music.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.sakayori.peek.toSvg

/**
 * Deterministic avatar for a username, rendered from peek-kotlin.
 * Same name always yields the same face on every device, no network or storage.
 * Static (non-animated) — the 100% UX rule: this only swaps the picture content.
 */
@Composable
fun PeekAvatar(
    name: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val svg = remember(name) { toSvg(name) }
    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(svg.toByteArray())
                .decoderFactory(SvgDecoder.Factory())
                .memoryCacheKey("peek-$name")
                .diskCacheKey("peek-$name")
                .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape),
    )
}
