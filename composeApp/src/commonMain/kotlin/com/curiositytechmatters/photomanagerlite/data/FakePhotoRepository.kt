package com.curiositytechmatters.photomanagerlite.data

import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import com.curiositytechmatters.photomanagerlite.model.PhotoSortOrder
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceAlbum
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceSettings
import com.curiositytechmatters.photomanagerlite.model.fakePhotos

/**
 * In-memory repository used by previews, denied-permission Android sessions, and fake-data flows.
 */
class FakePhotoRepository(
    private val photos: List<PhotoItem> = fakePhotos,
) : PhotoRepository {
    private companion object {
        val FAKE_SOURCE_ALBUM = PhotoSourceAlbum(
            id = "fake-source-album",
            name = "Fake Photos",
        )
    }

    private val reviewedPhotoIds = mutableSetOf<String>()
    private val sourcePhotos = photos.map { photo ->
        photo.copy(
            sourceAlbumId = photo.sourceAlbumId ?: FAKE_SOURCE_ALBUM.id,
            sourceAlbumName = photo.sourceAlbumName ?: FAKE_SOURCE_ALBUM.name,
        )
    }
    private var photoSourceSettings = PhotoSourceSettings()
    private var activePhotos = sourcePhotos.applyPhotoSourceSettings(photoSourceSettings)
    private var nextIndex = 0

    /**
     * Exposes the single fake album so the settings UI behaves like the Android source picker.
     */
    override suspend fun getSourceAlbums(): List<PhotoSourceAlbum> {
        return listOf(FAKE_SOURCE_ALBUM.copy(photoCount = sourcePhotos.size))
    }

    /**
     * Applies the selected fake album filters and resets the in-memory queue.
     */
    override suspend fun configurePhotoSource(settings: PhotoSourceSettings) {
        if (settings == photoSourceSettings) {
            return
        }

        photoSourceSettings = settings
        activePhotos = sourcePhotos.applyPhotoSourceSettings(settings)
        nextIndex = 0
    }

    /**
     * Returns the next fake photo that has not already been marked keep or remove.
     */
    override suspend fun getNextPhoto(): PhotoItem? {
        while (nextIndex < activePhotos.size) {
            val nextPhoto = activePhotos[nextIndex]
            nextIndex += 1
            if (nextPhoto.id !in reviewedPhotoIds) {
                return nextPhoto
            }
        }
        return null
    }

    /**
     * Marks a fake photo as reviewed and kept for the current in-memory session.
     */
    override suspend fun keepPhoto(photo: PhotoItem) {
        reviewedPhotoIds += photo.id
    }

    /**
     * Marks a fake photo as reviewed and removed for the current in-memory session.
     */
    override suspend fun requestRemovePhoto(photo: PhotoItem): Boolean {
        reviewedPhotoIds += photo.id
        return true
    }
}

private fun List<PhotoItem>.applyPhotoSourceSettings(
    settings: PhotoSourceSettings,
): List<PhotoItem> {
    val filteredPhotos = filter { photo ->
        val includeMatches = settings.includeSourceAlbum.albumId?.let { albumId ->
            photo.sourceAlbumId == albumId
        } ?: true
        val excludeMatches = settings.excludeSourceAlbum.albumId?.let { albumId ->
            photo.sourceAlbumId != albumId
        } ?: true

        includeMatches && excludeMatches
    }

    return when (settings.photoSortOrder) {
        PhotoSortOrder.Newest,
        PhotoSortOrder.Oldest -> filteredPhotos
        PhotoSortOrder.Random -> filteredPhotos.shuffled()
    }
}
