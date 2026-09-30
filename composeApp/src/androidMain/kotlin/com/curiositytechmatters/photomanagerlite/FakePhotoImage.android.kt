package com.curiositytechmatters.photomanagerlite

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android image renderer for both real MediaStore photos and bundled fake photos.
 */
@Composable
actual fun PhotoImage(
    photo: PhotoItem,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val contentUri = photo.contentUri

    if (contentUri != null) {
        // Real Android photos arrive as content URIs from MediaStore.
        AndroidContentPhotoImage(
            contentUri = contentUri,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }

    Image(
        painter = painterResource(drawableIdFor(photo.resourcePath)),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

/**
 * Loads a MediaStore content URI off the main thread and renders it into the photo card.
 */
@Composable
private fun AndroidContentPhotoImage(
    contentUri: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val context = LocalContext.current
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, contentUri) {
        value = withContext(Dispatchers.IO) {
            val uri = Uri.parse(contentUri)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Load a bounded thumbnail to avoid decoding full-resolution camera images into memory.
                    context.contentResolver.loadThumbnail(
                        uri,
                        Size(1200, 1800),
                        null,
                    ).asImageBitmap()
                } else {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    if (imageBitmap == null) {
        Box(
            modifier = modifier.background(MaterialTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Loading photo",
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.64f),
            )
        }
    } else {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

/**
 * Maps bundled fake photo paths to Android drawable resources for the fallback review flow.
 */
private fun drawableIdFor(resourcePath: String?): Int {
    return when (resourcePath) {
        "fake_photos/portrait_01.png" -> R.drawable.portrait_01
        "fake_photos/portrait_02.png" -> R.drawable.portrait_02
        "fake_photos/portrait_03.png" -> R.drawable.portrait_03
        "fake_photos/portrait_04.png" -> R.drawable.portrait_04
        "fake_photos/portrait_05.png" -> R.drawable.portrait_05
        "fake_photos/portrait_06.png" -> R.drawable.portrait_06
        "fake_photos/portrait_07.png" -> R.drawable.portrait_07
        "fake_photos/portrait_08.png" -> R.drawable.portrait_08
        "fake_photos/portrait_09.png" -> R.drawable.portrait_09
        "fake_photos/portrait_10.png" -> R.drawable.portrait_10
        else -> R.drawable.portrait_01
    }
}
