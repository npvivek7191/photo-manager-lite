package com.curiositytechmatters.photomanagerlite.data

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.curiositytechmatters.photomanagerlite.model.UNKNOWN_LOCATION_BUCKET
import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import com.curiositytechmatters.photomanagerlite.model.PhotoSortOrder
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceAlbum
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation that scans image candidates from MediaStore and requests safe trashing.
 *
 * Permanent delete is intentionally not supported. Remove requests go through Android's system
 * confirmation UI before the app advances to the next photo.
 */
class AndroidPhotoRepository(
    context: Context,
    private val trashRequester: suspend (IntentSender) -> Boolean = { false },
) : PhotoRepository {
    private companion object {
        const val PHOTO_SCAN_LIMIT = 250
        const val UNKNOWN_SOURCE_ALBUM_NAME = "Unknown album"
    }

    private val contentResolver = context.applicationContext.contentResolver
    private val reviewedPhotoIds = mutableSetOf<String>()
    private var photoSourceSettings = PhotoSourceSettings()
    private var cachedPhotos: List<PhotoItem>? = null
    private var nextIndex = 0

    /**
     * Reads MediaStore bucket metadata for the source include/exclude settings.
     */
    override suspend fun getSourceAlbums(): List<PhotoSourceAlbum> = withContext(Dispatchers.IO) {
        querySourceAlbums()
    }

    /**
     * Invalidates the current queue when source filters or sort order change.
     */
    override suspend fun configurePhotoSource(settings: PhotoSourceSettings) {
        if (settings == photoSourceSettings) {
            return
        }

        photoSourceSettings = settings
        cachedPhotos = null
        nextIndex = 0
    }

    /**
     * Returns the next MediaStore photo that has not already been reviewed this session.
     */
    override suspend fun getNextPhoto(): PhotoItem? = withContext(Dispatchers.IO) {
        val photos = cachedPhotos ?: queryPhotos().also { cachedPhotos = it }

        while (nextIndex < photos.size) {
            val nextPhoto = photos[nextIndex]
            nextIndex += 1
            if (nextPhoto.id !in reviewedPhotoIds) {
                return@withContext nextPhoto
            }
        }

        null
    }

    /**
     * Records a keep decision in memory for the current app session.
     */
    override suspend fun keepPhoto(photo: PhotoItem) {
        reviewedPhotoIds += photo.id
    }

    /**
     * Requests that Android move the photo to trash and records it only after confirmation.
     */
    override suspend fun requestRemovePhoto(photo: PhotoItem): Boolean {
        val contentUri = photo.contentUri?.let(Uri::parse) ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }

        val trashIntentSender = withContext(Dispatchers.IO) {
            try {
                MediaStore.createTrashRequest(
                    contentResolver,
                    listOf(contentUri),
                    true,
                ).intentSender
            } catch (_: Exception) {
                null
            }
        } ?: return false

        val didTrash = trashRequester(trashIntentSender)
        if (!didTrash) {
            return false
        }

        reviewedPhotoIds += photo.id
        return true
    }

    /**
     * Queries MediaStore for images and maps Android cursor rows into shared PhotoItem values.
     */
    private fun queryPhotos(): List<PhotoItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val activeSourceSettings = photoSourceSettings
        val mediaStoreFilter = activeSourceSettings.toMediaStoreFilter()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val sortOrder = activeSourceSettings.photoSortOrder.toMediaStoreSortOrder()
        val photos = mutableListOf<PhotoItem>()

        try {
            // MediaStore access is permission-gated, so a denied or revoked permission returns no photos.
            contentResolver.query(
                collection,
                projection,
                mediaStoreFilter.selection,
                mediaStoreFilter.selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.DISPLAY_NAME
                )
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sourceAlbumIdColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.BUCKET_ID
                )
                val sourceAlbumNameColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )

                while (cursor.moveToNext() && photos.size < PHOTO_SCAN_LIMIT) {
                    val mediaId = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val dateTakenMillis = cursor.getLong(dateTakenColumn).takeIf { it > 0 }
                        ?: cursor.getLong(dateAddedColumn).takeIf { it > 0 }?.times(1000)
                    val dateTakenLabel = dateTakenMillis?.toPhotoDateLabel()
                    val sourceAlbumId = cursor.getString(sourceAlbumIdColumn)
                    val sourceAlbumName = cursor.getString(sourceAlbumNameColumn)
                        ?.takeIf { it.isNotBlank() }
                        ?: UNKNOWN_SOURCE_ALBUM_NAME
                    val contentUri = ContentUris.withAppendedId(collection, mediaId)
                    val coordinates = coordinatesFor(contentUri)

                    photos += PhotoItem(
                        id = "media:$mediaId",
                        contentUri = contentUri.toString(),
                        sourceAlbumId = sourceAlbumId,
                        sourceAlbumName = sourceAlbumName,
                        locationBucket = if (coordinates == null) {
                            dateTakenLabel?.let { "Photos from $it" } ?: UNKNOWN_LOCATION_BUCKET
                        } else {
                            UNKNOWN_LOCATION_BUCKET
                        },
                        latitude = coordinates?.first,
                        longitude = coordinates?.second,
                        displayName = displayName,
                        dateTakenMillis = dateTakenMillis,
                        dateTakenLabel = dateTakenLabel,
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }

        return if (activeSourceSettings.photoSortOrder == PhotoSortOrder.Random) {
            photos.shuffled()
        } else {
            photos
        }
    }

    /**
     * Builds a device album list from MediaStore bucket columns.
     */
    private fun querySourceAlbums(): List<PhotoSourceAlbum> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val albumsById = linkedMapOf<String, SourceAlbumAccumulator>()

        try {
            contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val sourceAlbumIdColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.BUCKET_ID
                )
                val sourceAlbumNameColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )

                while (cursor.moveToNext()) {
                    val sourceAlbumId = cursor.getString(sourceAlbumIdColumn) ?: continue
                    val sourceAlbumName = cursor.getString(sourceAlbumNameColumn)
                        ?.takeIf { it.isNotBlank() }
                        ?: UNKNOWN_SOURCE_ALBUM_NAME
                    val accumulator = albumsById.getOrPut(sourceAlbumId) {
                        SourceAlbumAccumulator(
                            id = sourceAlbumId,
                            name = sourceAlbumName,
                        )
                    }

                    accumulator.photoCount += 1
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }

        return albumsById.values
            .map { album ->
                PhotoSourceAlbum(
                    id = album.id,
                    name = album.name,
                    photoCount = album.photoCount,
                )
            }
            .sortedWith(
                compareBy<PhotoSourceAlbum> { it.name.lowercase(Locale.getDefault()) }
                    .thenBy { it.id }
            )
    }

    /**
     * Reads raw GPS latitude and longitude from EXIF metadata when Android allows access to it.
     *
     * The chosen coarse bucket size is applied later in shared AppState using the user's setting.
     */
    private fun coordinatesFor(contentUri: Uri): Pair<Double, Double>? {
        val originalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.setRequireOriginal(contentUri)
        } else {
            contentUri
        }

        return readExifCoordinates(originalUri) ?: readExifCoordinates(contentUri)
    }

    /**
     * Opens a content URI and extracts EXIF GPS coordinates from the image stream.
     */
    private fun readExifCoordinates(contentUri: Uri): Pair<Double, Double>? {
        return try {
            contentResolver.openInputStream(contentUri)?.use { inputStream ->
                val output = FloatArray(size = 2)
                val hasCoordinates = ExifInterface(inputStream).getLatLong(output)
                if (hasCoordinates) {
                    output[0].toDouble() to output[1].toDouble()
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

private fun Long.toPhotoDateLabel(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(this))
}

private data class MediaStoreFilter(
    val selection: String?,
    val selectionArgs: Array<String>?,
)

private data class SourceAlbumAccumulator(
    val id: String,
    val name: String,
    var photoCount: Int = 0,
)

private fun PhotoSourceSettings.toMediaStoreFilter(): MediaStoreFilter {
    val includeAlbumId = includeSourceAlbum.albumId
    if (includeAlbumId != null) {
        return MediaStoreFilter(
            selection = "${MediaStore.Images.Media.BUCKET_ID} = ?",
            selectionArgs = arrayOf(includeAlbumId),
        )
    }

    val excludeAlbumId = excludeSourceAlbum.albumId
    if (excludeAlbumId != null) {
        return MediaStoreFilter(
            selection = "${MediaStore.Images.Media.BUCKET_ID} != ?",
            selectionArgs = arrayOf(excludeAlbumId),
        )
    }

    return MediaStoreFilter(
        selection = null,
        selectionArgs = null,
    )
}

private fun PhotoSortOrder.toMediaStoreSortOrder(): String {
    return when (this) {
        PhotoSortOrder.Newest,
        PhotoSortOrder.Random -> "${MediaStore.Images.Media.DATE_TAKEN} DESC, " +
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        PhotoSortOrder.Oldest -> "${MediaStore.Images.Media.DATE_TAKEN} ASC, " +
            "${MediaStore.Images.Media.DATE_ADDED} ASC"
    }
}
