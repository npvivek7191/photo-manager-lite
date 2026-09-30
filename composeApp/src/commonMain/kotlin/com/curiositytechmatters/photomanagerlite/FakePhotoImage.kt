package com.curiositytechmatters.photomanagerlite

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.curiositytechmatters.photomanagerlite.model.PhotoItem

/**
 * Platform image renderer for a photo card.
 *
 * Android can render either MediaStore content URIs or bundled fake resources; other platforms
 * currently render the bundled fake resources.
 */
@Composable
expect fun PhotoImage(
    photo: PhotoItem,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
)
