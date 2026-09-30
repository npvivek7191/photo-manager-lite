package com.curiositytechmatters.photomanagerlite.data

import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceAlbum
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceSettings

/**
 * Shared boundary for photo review data.
 *
 * Common UI code depends on this interface so Android-specific APIs stay inside androidMain.
 */
interface PhotoRepository {
    /**
     * Returns device albums that can be used as include/exclude photo-source filters.
     */
    suspend fun getSourceAlbums(): List<PhotoSourceAlbum> {
        return emptyList()
    }

    /**
     * Applies source filters and sort order before the next review queue is loaded.
     */
    suspend fun configurePhotoSource(settings: PhotoSourceSettings) = Unit

    /**
     * Returns the next photo candidate for review, or null when no candidate is available.
     */
    suspend fun getNextPhoto(): PhotoItem?

    /**
     * Records that a photo should be kept and excluded from future remove suggestions.
     */
    suspend fun keepPhoto(photo: PhotoItem)

    /**
     * Requests removal for a photo.
     *
     * Returns true only when the photo was removed or accepted for removal. Android implementations
     * can return false when the user cancels the system trash confirmation.
     */
    suspend fun requestRemovePhoto(photo: PhotoItem): Boolean
}
