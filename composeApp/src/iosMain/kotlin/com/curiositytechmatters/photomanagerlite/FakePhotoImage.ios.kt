package com.curiositytechmatters.photomanagerlite

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

/**
 * iOS placeholder image renderer for the current fake-photo-only target.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
actual fun PhotoImage(
    photo: PhotoItem,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    Image(
        painter = painterResource(photo.resourcePath ?: "fake_photos/portrait_01.png"),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
