package com.curiositytechmatters.photomanagerlite.model

data class AppSettings(
    val includeSourceAlbum: PhotoSourceAlbumSelection = PhotoSourceAlbumSelection.All,
    val excludeSourceAlbum: PhotoSourceAlbumSelection = PhotoSourceAlbumSelection.None,
    val photoSortOrder: PhotoSortOrder = PhotoSortOrder.Newest,
    val notificationsEnabled: Boolean = true,
    val notificationFrequency: NotificationFrequency = NotificationFrequency.Daily,
    val preferredNotificationStartTime: PreferredNotificationStartTime =
        PreferredNotificationStartTime(),
    val weeklyReviewDay: WeeklyReviewDay = WeeklyReviewDay.Monday,
    val confirmBeforeRemove: Boolean = true,
    val albumSuggestionsEnabled: Boolean = true,
    val removeLocationSuggestionsEnabled: Boolean = true,
    val suggestionThreshold: Int = 3,
    val albumSize: Int = 30,
    val locationBucketRadiusKm: Double = 1.0,
)

val locationBucketRadiusKmOptions = listOf(0.1, 1.0, 10.0, 100.0)

/**
 * Device album metadata exposed to the shared settings UI.
 */
data class PhotoSourceAlbum(
    val id: String,
    val name: String,
    val photoCount: Int = 0,
) {
    val label: String
        get() = if (photoCount > 0) "$name ($photoCount)" else name
}

/**
 * A single selected source album, or the empty selection used by All/None.
 */
data class PhotoSourceAlbumSelection(
    val albumId: String? = null,
    val albumName: String? = null,
) {
    val isSpecificAlbum: Boolean
        get() = albumId != null

    fun label(defaultLabel: String): String {
        return albumName?.takeIf { it.isNotBlank() } ?: defaultLabel
    }

    companion object {
        val All = PhotoSourceAlbumSelection()
        val None = PhotoSourceAlbumSelection()

        fun specific(album: PhotoSourceAlbum): PhotoSourceAlbumSelection {
            return PhotoSourceAlbumSelection(
                albumId = album.id,
                albumName = album.name,
            )
        }
    }
}

/**
 * Source options that require the repository to rebuild the review queue.
 */
data class PhotoSourceSettings(
    val includeSourceAlbum: PhotoSourceAlbumSelection = PhotoSourceAlbumSelection.All,
    val excludeSourceAlbum: PhotoSourceAlbumSelection = PhotoSourceAlbumSelection.None,
    val photoSortOrder: PhotoSortOrder = PhotoSortOrder.Newest,
)

val AppSettings.photoSourceSettings: PhotoSourceSettings
    get() = PhotoSourceSettings(
        includeSourceAlbum = includeSourceAlbum,
        excludeSourceAlbum = excludeSourceAlbum,
        photoSortOrder = photoSortOrder,
    )

data class PreferredNotificationStartTime(
    val hour: Int = 9,
    val minute: Int = 0,
) {
    val label: String
        get() = "${hour.twoDigitString()}:${minute.twoDigitString()}"
}

enum class NotificationFrequency(val label: String) {
    TwelveHours("Every 12 hours"),
    Daily("Every day"),
    AlternateDays("Alternate days"),
    Weekly("Once a week"),
}

enum class PhotoSortOrder(val label: String) {
    Newest("Newest"),
    Oldest("Oldest"),
    Random("Random"),
}

enum class WeeklyReviewDay(val label: String) {
    Monday("Monday"),
    Tuesday("Tuesday"),
    Wednesday("Wednesday"),
    Thursday("Thursday"),
    Friday("Friday"),
    Saturday("Saturday"),
    Sunday("Sunday"),
}

private fun Int.twoDigitString(): String {
    return toString().padStart(length = 2, padChar = '0')
}
