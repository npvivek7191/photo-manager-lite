package com.curiositytechmatters.photomanagerlite.data

import com.curiositytechmatters.photomanagerlite.model.AppSettings
import com.curiositytechmatters.photomanagerlite.model.PhotoDecision
import com.curiositytechmatters.photomanagerlite.model.PhotoItem

/**
 * Shared persistence boundary for settings and durable photo review decisions.
 *
 * Android currently implements this with SharedPreferences so the project does not need a new
 * dependency. The interface keeps the app ready for a later DataStore implementation.
 */
interface AppStorage {
    /**
     * Loads stored settings, or defaults when nothing has been saved yet.
     */
    suspend fun loadSettings(): AppSettings

    /**
     * Persists the full settings snapshot.
     */
    suspend fun saveSettings(settings: AppSettings)

    /**
     * Loads stored decisions keyed by stable photo identity.
     */
    suspend fun loadDecisions(): List<PhotoDecisionRecord>

    /**
     * Persists the latest review decision for a photo.
     */
    suspend fun saveDecision(
        photo: PhotoItem,
        decision: PhotoDecision,
    )
}

/**
 * No-op implementation for previews and targets without durable storage yet.
 */
class NoOpAppStorage : AppStorage {
    override suspend fun loadSettings(): AppSettings {
        return AppSettings()
    }

    override suspend fun saveSettings(settings: AppSettings) = Unit

    override suspend fun loadDecisions(): List<PhotoDecisionRecord> {
        return emptyList()
    }

    override suspend fun saveDecision(
        photo: PhotoItem,
        decision: PhotoDecision,
    ) = Unit
}

data class PhotoDecisionRecord(
    val photoId: String,
    val contentUri: String?,
    val displayName: String?,
    val dateTakenMillis: Long?,
    val dateTakenLabel: String?,
    val locationBucket: String,
    val latitude: Double?,
    val longitude: Double?,
    val decision: StoredPhotoDecision,
    val decidedAtMillis: Long,
)

enum class StoredPhotoDecision {
    Keep,
    Remove,
    Skipped,
}

fun PhotoDecision.toStoredDecision(): StoredPhotoDecision {
    return when (this) {
        PhotoDecision.Keep -> StoredPhotoDecision.Keep
        PhotoDecision.Remove -> StoredPhotoDecision.Remove
        PhotoDecision.Later -> StoredPhotoDecision.Skipped
    }
}

fun List<PhotoItem>.withoutStoredKeepOrRemoveDecisions(
    decisions: List<PhotoDecisionRecord>,
): List<PhotoItem> {
    val hiddenPhotoIds = decisions.asSequence()
        .filter {
            it.decision == StoredPhotoDecision.Keep ||
                it.decision == StoredPhotoDecision.Remove
        }
        .map { it.photoId }
        .toSet()

    return filterNot { photo -> photo.id in hiddenPhotoIds }
}
