package com.curiositytechmatters.photomanagerlite.data

import android.content.Context
import android.content.SharedPreferences
import com.curiositytechmatters.photomanagerlite.model.AppSettings
import com.curiositytechmatters.photomanagerlite.model.PhotoDecision
import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceAlbumSelection
import com.curiositytechmatters.photomanagerlite.model.PreferredNotificationStartTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Android persistence for settings and photo decisions.
 *
 * This uses SharedPreferences for the current prototype because DataStore is not wired into the
 * project yet. The shared AppStorage interface keeps this implementation replaceable.
 */
class AndroidAppStorage(
    context: Context,
) : AppStorage {
    private companion object {
        const val PREFS_NAME = "photo_manager_lite_storage"
        const val KEY_INCLUDE_SOURCE_ALBUM_ID = "include_source_album_id"
        const val KEY_INCLUDE_SOURCE_ALBUM_NAME = "include_source_album_name"
        const val KEY_EXCLUDE_SOURCE_ALBUM_ID = "exclude_source_album_id"
        const val KEY_EXCLUDE_SOURCE_ALBUM_NAME = "exclude_source_album_name"
        const val KEY_PHOTO_SORT_ORDER = "photo_sort_order"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_NOTIFICATION_FREQUENCY = "notification_frequency"
        const val KEY_PREFERRED_NOTIFICATION_START_HOUR =
            "preferred_notification_start_hour"
        const val KEY_PREFERRED_NOTIFICATION_START_MINUTE =
            "preferred_notification_start_minute"
        const val KEY_WEEKLY_REVIEW_DAY = "weekly_review_day"
        const val KEY_CONFIRM_BEFORE_REMOVE = "confirm_before_remove"
        const val KEY_ALBUM_SUGGESTIONS_ENABLED = "album_suggestions_enabled"
        const val KEY_REMOVE_LOCATION_SUGGESTIONS_ENABLED = "remove_location_suggestions_enabled"
        const val KEY_SUGGESTION_THRESHOLD = "suggestion_threshold"
        const val KEY_ALBUM_SIZE = "album_size"
        const val KEY_LOCATION_BUCKET_RADIUS_KM = "location_bucket_radius_km"
        const val KEY_DECISIONS = "photo_decisions"
    }

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )
    private val decisionsLock = Any()

    /**
     * Loads the saved settings snapshot, falling back field-by-field to defaults.
     */
    override suspend fun loadSettings(): AppSettings = withContext(Dispatchers.IO) {
        val defaults = AppSettings()
        AppSettings(
            includeSourceAlbum = preferences.albumSelection(
                idKey = KEY_INCLUDE_SOURCE_ALBUM_ID,
                nameKey = KEY_INCLUDE_SOURCE_ALBUM_NAME,
                defaultValue = defaults.includeSourceAlbum,
            ),
            excludeSourceAlbum = preferences.albumSelection(
                idKey = KEY_EXCLUDE_SOURCE_ALBUM_ID,
                nameKey = KEY_EXCLUDE_SOURCE_ALBUM_NAME,
                defaultValue = defaults.excludeSourceAlbum,
            ),
            photoSortOrder = preferences.enumValue(
                key = KEY_PHOTO_SORT_ORDER,
                defaultValue = defaults.photoSortOrder,
            ),
            notificationsEnabled = preferences.getBoolean(
                KEY_NOTIFICATIONS_ENABLED,
                defaults.notificationsEnabled,
            ),
            notificationFrequency = preferences.enumValue(
                key = KEY_NOTIFICATION_FREQUENCY,
                defaultValue = defaults.notificationFrequency,
            ),
            preferredNotificationStartTime = PreferredNotificationStartTime(
                hour = preferences.getInt(
                    KEY_PREFERRED_NOTIFICATION_START_HOUR,
                    defaults.preferredNotificationStartTime.hour,
                ).coerceIn(0, 23),
                minute = preferences.getInt(
                    KEY_PREFERRED_NOTIFICATION_START_MINUTE,
                    defaults.preferredNotificationStartTime.minute,
                ).coerceIn(0, 59),
            ),
            weeklyReviewDay = preferences.enumValue(
                key = KEY_WEEKLY_REVIEW_DAY,
                defaultValue = defaults.weeklyReviewDay,
            ),
            confirmBeforeRemove = preferences.getBoolean(
                KEY_CONFIRM_BEFORE_REMOVE,
                defaults.confirmBeforeRemove,
            ),
            albumSuggestionsEnabled = preferences.getBoolean(
                KEY_ALBUM_SUGGESTIONS_ENABLED,
                defaults.albumSuggestionsEnabled,
            ),
            removeLocationSuggestionsEnabled = preferences.getBoolean(
                KEY_REMOVE_LOCATION_SUGGESTIONS_ENABLED,
                defaults.removeLocationSuggestionsEnabled,
            ),
            suggestionThreshold = preferences.getInt(
                KEY_SUGGESTION_THRESHOLD,
                defaults.suggestionThreshold,
            ),
            albumSize = preferences.getInt(
                KEY_ALBUM_SIZE,
                defaults.albumSize,
            ),
            locationBucketRadiusKm = preferences.getString(
                KEY_LOCATION_BUCKET_RADIUS_KM,
                defaults.locationBucketRadiusKm.toString(),
            )?.toDoubleOrNull() ?: defaults.locationBucketRadiusKm,
        )
    }

    /**
     * Saves the full settings snapshot.
     */
    override suspend fun saveSettings(settings: AppSettings) {
        withContext(Dispatchers.IO) {
            preferences.edit()
                .putString(KEY_INCLUDE_SOURCE_ALBUM_ID, settings.includeSourceAlbum.albumId)
                .putString(KEY_INCLUDE_SOURCE_ALBUM_NAME, settings.includeSourceAlbum.albumName)
                .putString(KEY_EXCLUDE_SOURCE_ALBUM_ID, settings.excludeSourceAlbum.albumId)
                .putString(KEY_EXCLUDE_SOURCE_ALBUM_NAME, settings.excludeSourceAlbum.albumName)
                .putString(KEY_PHOTO_SORT_ORDER, settings.photoSortOrder.name)
                .putBoolean(KEY_NOTIFICATIONS_ENABLED, settings.notificationsEnabled)
                .putString(KEY_NOTIFICATION_FREQUENCY, settings.notificationFrequency.name)
                .putInt(
                    KEY_PREFERRED_NOTIFICATION_START_HOUR,
                    settings.preferredNotificationStartTime.hour,
                )
                .putInt(
                    KEY_PREFERRED_NOTIFICATION_START_MINUTE,
                    settings.preferredNotificationStartTime.minute,
                )
                .putString(KEY_WEEKLY_REVIEW_DAY, settings.weeklyReviewDay.name)
                .putBoolean(KEY_CONFIRM_BEFORE_REMOVE, settings.confirmBeforeRemove)
                .putBoolean(KEY_ALBUM_SUGGESTIONS_ENABLED, settings.albumSuggestionsEnabled)
                .putBoolean(
                    KEY_REMOVE_LOCATION_SUGGESTIONS_ENABLED,
                    settings.removeLocationSuggestionsEnabled,
                )
                .putInt(KEY_SUGGESTION_THRESHOLD, settings.suggestionThreshold)
                .putInt(KEY_ALBUM_SIZE, settings.albumSize)
                .putString(
                    KEY_LOCATION_BUCKET_RADIUS_KM,
                    settings.locationBucketRadiusKm.toString(),
                )
                .apply()
        }
    }

    /**
     * Loads all stored decision records.
     */
    override suspend fun loadDecisions(): List<PhotoDecisionRecord> = withContext(Dispatchers.IO) {
        synchronized(decisionsLock) {
            val storedDecisions = decisionsJson()
            storedDecisions.keys().asSequence()
                .mapNotNull { photoId ->
                    storedDecisions.optJSONObject(photoId)?.toDecisionRecord()
                }
                .toList()
        }
    }

    /**
     * Saves the latest decision for the photo's stable ID.
     */
    override suspend fun saveDecision(
        photo: PhotoItem,
        decision: PhotoDecision,
    ) {
        withContext(Dispatchers.IO) {
            synchronized(decisionsLock) {
                val nextDecisions = decisionsJson()
                val record = PhotoDecisionRecord(
                    photoId = photo.id,
                    contentUri = photo.contentUri,
                    displayName = photo.displayName,
                    dateTakenMillis = photo.dateTakenMillis,
                    dateTakenLabel = photo.dateTakenLabel,
                    locationBucket = photo.locationBucket,
                    latitude = photo.latitude,
                    longitude = photo.longitude,
                    decision = decision.toStoredDecision(),
                    decidedAtMillis = System.currentTimeMillis(),
                )

                nextDecisions.put(photo.id, record.toJson())
                preferences.edit()
                    .putString(KEY_DECISIONS, nextDecisions.toString())
                    .apply()
            }
        }
    }

    private fun decisionsJson(): JSONObject {
        val storedJson = preferences.getString(KEY_DECISIONS, null) ?: return JSONObject()
        return runCatching { JSONObject(storedJson) }.getOrElse { JSONObject() }
    }
}

private inline fun <reified T : Enum<T>> SharedPreferences.enumValue(
    key: String,
    defaultValue: T,
): T {
    val storedName = getString(key, null) ?: return defaultValue
    return enumValues<T>().firstOrNull { it.name == storedName } ?: defaultValue
}

private fun SharedPreferences.albumSelection(
    idKey: String,
    nameKey: String,
    defaultValue: PhotoSourceAlbumSelection,
): PhotoSourceAlbumSelection {
    val albumId = getString(idKey, null)?.takeIf { it.isNotBlank() }
        ?: return defaultValue

    return PhotoSourceAlbumSelection(
        albumId = albumId,
        albumName = getString(nameKey, null)?.takeIf { it.isNotBlank() },
    )
}

private fun PhotoDecisionRecord.toJson(): JSONObject {
    return JSONObject()
        .put("photoId", photoId)
        .putNullable("contentUri", contentUri)
        .putNullable("displayName", displayName)
        .putNullable("dateTakenMillis", dateTakenMillis)
        .putNullable("dateTakenLabel", dateTakenLabel)
        .put("locationBucket", locationBucket)
        .putNullable("latitude", latitude)
        .putNullable("longitude", longitude)
        .put("decision", decision.name)
        .put("decidedAtMillis", decidedAtMillis)
}

private fun JSONObject.toDecisionRecord(): PhotoDecisionRecord? {
    val photoId = nullableString("photoId") ?: return null
    val decision = enumValues<StoredPhotoDecision>()
        .firstOrNull { it.name == nullableString("decision") }
        ?: return null

    return PhotoDecisionRecord(
        photoId = photoId,
        contentUri = nullableString("contentUri"),
        displayName = nullableString("displayName"),
        dateTakenMillis = nullableLong("dateTakenMillis"),
        dateTakenLabel = nullableString("dateTakenLabel"),
        locationBucket = nullableString("locationBucket") ?: "",
        latitude = nullableDouble("latitude"),
        longitude = nullableDouble("longitude"),
        decision = decision,
        decidedAtMillis = nullableLong("decidedAtMillis") ?: 0L,
    )
}

private fun JSONObject.putNullable(
    name: String,
    value: Any?,
): JSONObject {
    return if (value == null) {
        put(name, JSONObject.NULL)
    } else {
        put(name, value)
    }
}

private fun JSONObject.nullableString(name: String): String? {
    return if (isNull(name)) null else optString(name)
}

private fun JSONObject.nullableLong(name: String): Long? {
    return if (isNull(name)) null else optLong(name)
}

private fun JSONObject.nullableDouble(name: String): Double? {
    return if (isNull(name)) null else optDouble(name)
}
