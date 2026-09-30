package com.curiositytechmatters.photomanagerlite.model

data class AppState(
    val photos: List<PhotoItem> = fakePhotos,
    val currentPhotoIndex: Int = 0,
    val keptPhotos: List<PhotoItem> = emptyList(),
    val removedPhotos: List<PhotoItem> = emptyList(),
    val skippedPhotos: List<PhotoItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val albumSuggestions: List<AlbumSuggestion> = emptyList(),
    val activeAlbumSuggestion: AlbumSuggestion? = null,
    val currentKeepStreak: LocationDecisionStreak = LocationDecisionStreak(),
    val currentRemoveStreak: LocationDecisionStreak = LocationDecisionStreak(),
    val lastActionLabel: String = "Ready to review",
) {
    val currentPhoto: PhotoItem?
        get() = photos.getOrNull(currentPhotoIndex)

    /**
     * Replaces the current review queue and clears review-only state for a fresh photo source.
     */
    fun withPhotos(newPhotos: List<PhotoItem>): AppState {
        val bucketedPhotos = newPhotos.withLocationBucketRadius(settings.locationBucketRadiusKm)
        return copy(
            photos = bucketedPhotos,
            currentPhotoIndex = 0,
            keptPhotos = emptyList(),
            removedPhotos = emptyList(),
            skippedPhotos = emptyList(),
            albumSuggestions = emptyList(),
            activeAlbumSuggestion = null,
            currentKeepStreak = LocationDecisionStreak(),
            currentRemoveStreak = LocationDecisionStreak(),
            lastActionLabel = if (bucketedPhotos.isEmpty()) {
                "No photos available"
            } else {
                "Loaded ${bucketedPhotos.size} photos"
            },
        )
    }

    fun withSettings(newSettings: AppSettings): AppState {
        val locationRadiusChanged = settings.locationBucketRadiusKm !=
            newSettings.locationBucketRadiusKm
        val nextActiveAlbumSuggestion = activeAlbumSuggestion?.takeIf { suggestion ->
            suggestion.isAllowedBy(newSettings)
        }

        if (!locationRadiusChanged) {
            return copy(
                settings = newSettings,
                activeAlbumSuggestion = nextActiveAlbumSuggestion,
            )
        }

        return copy(
            settings = newSettings,
            photos = photos.withLocationBucketRadius(newSettings.locationBucketRadiusKm),
            keptPhotos = keptPhotos.withLocationBucketRadius(newSettings.locationBucketRadiusKm),
            removedPhotos = removedPhotos.withLocationBucketRadius(
                newSettings.locationBucketRadiusKm
            ),
            skippedPhotos = skippedPhotos.withLocationBucketRadius(newSettings.locationBucketRadiusKm),
            albumSuggestions = emptyList(),
            activeAlbumSuggestion = null,
            currentKeepStreak = LocationDecisionStreak(),
            currentRemoveStreak = LocationDecisionStreak(),
            lastActionLabel = "Location grouping set to ${
                locationBucketRadiusLabel(newSettings.locationBucketRadiusKm)
            }",
        )
    }

    fun reviewCurrentPhoto(decision: PhotoDecision): AppState {
        val photo = currentPhoto ?: return this
        val nextKeepStreak = when (decision) {
            PhotoDecision.Keep -> currentKeepStreak.advancedFor(photo.locationBucket)
            PhotoDecision.Remove -> LocationDecisionStreak()
            PhotoDecision.Later -> LocationDecisionStreak()
        }
        val nextRemoveStreak = when (decision) {
            PhotoDecision.Keep -> LocationDecisionStreak()
            PhotoDecision.Remove -> currentRemoveStreak.advancedFor(photo.locationBucket)
            PhotoDecision.Later -> LocationDecisionStreak()
        }
        val nextSuggestion = suggestionFor(
            decision = decision,
            photo = photo,
            keepStreak = nextKeepStreak,
            removeStreak = nextRemoveStreak,
        )

        return copy(
            currentPhotoIndex = nextPhotoIndex(),
            keptPhotos = when (decision) {
                PhotoDecision.Keep -> keptPhotos.withUniquePhoto(photo)
                PhotoDecision.Remove -> keptPhotos.withoutPhoto(photo)
                PhotoDecision.Later -> keptPhotos
            },
            removedPhotos = when (decision) {
                PhotoDecision.Keep -> removedPhotos.withoutPhoto(photo)
                PhotoDecision.Remove -> removedPhotos.withUniquePhoto(photo)
                PhotoDecision.Later -> removedPhotos
            },
            skippedPhotos = when (decision) {
                PhotoDecision.Keep -> skippedPhotos.withoutPhoto(photo)
                PhotoDecision.Remove -> skippedPhotos.withoutPhoto(photo)
                PhotoDecision.Later -> skippedPhotos.withUniquePhoto(photo)
            },
            currentKeepStreak = nextKeepStreak,
            currentRemoveStreak = nextRemoveStreak,
            activeAlbumSuggestion = nextSuggestion,
            albumSuggestions = if (nextSuggestion == null) {
                albumSuggestions
            } else {
                albumSuggestions.upsert(nextSuggestion)
            },
            lastActionLabel = when (decision) {
                PhotoDecision.Keep -> "Kept ${photo.id}"
                PhotoDecision.Remove -> "Removed ${photo.id}"
                PhotoDecision.Later -> "Skipped ${photo.id}"
            },
        )
    }

    fun requestAlbumSuggestionForCurrentPhoto(): AppState {
        if (!settings.albumSuggestionsEnabled) {
            return this
        }

        val photo = currentPhoto ?: return this
        val suggestion = albumSuggestionFor(
            idPrefix = "ManualKeepLocation",
            type = AlbumSuggestionType.KeepLocation,
            photo = photo,
            triggeringStreakCount = 0,
        )

        return copy(
            activeAlbumSuggestion = suggestion,
            albumSuggestions = albumSuggestions.upsert(suggestion),
        )
    }

    fun acceptActiveAlbumSuggestion(albumName: String? = null): AppState {
        val suggestion = activeAlbumSuggestion ?: return this
        val acceptedSuggestion = suggestion.copy(
            recommendedAlbumName = albumName?.takeIf { it.isNotBlank() }
                ?: suggestion.recommendedAlbumName,
            status = AlbumSuggestionStatus.Accepted,
        )
        val label = when (acceptedSuggestion.type) {
            AlbumSuggestionType.KeepLocation ->
                "Saved album rule \"${acceptedSuggestion.recommendedAlbumName}\""
            AlbumSuggestionType.RemoveLocation ->
                "Saved remove-location review for ${acceptedSuggestion.locationBucket}"
        }

        return copy(
            albumSuggestions = albumSuggestions.upsert(acceptedSuggestion),
            activeAlbumSuggestion = null,
            lastActionLabel = label,
        )
    }

    fun dismissActiveAlbumSuggestion(): AppState {
        val suggestion = activeAlbumSuggestion ?: return this
        return copy(
            albumSuggestions = albumSuggestions.upsert(
                suggestion.copy(status = AlbumSuggestionStatus.Dismissed)
            ),
            activeAlbumSuggestion = null,
            lastActionLabel = "Dismissed suggestion for ${suggestion.locationBucket}",
        )
    }

    /**
     * Moves forward through the queue without wrapping after repository-backed loading.
     */
    private fun nextPhotoIndex(): Int {
        return (currentPhotoIndex + 1).coerceAtMost(photos.size)
    }

    private fun suggestionFor(
        decision: PhotoDecision,
        photo: PhotoItem,
        keepStreak: LocationDecisionStreak,
        removeStreak: LocationDecisionStreak,
    ): AlbumSuggestion? {
        if (!settings.albumSuggestionsEnabled) {
            return null
        }

        val streak = when (decision) {
            PhotoDecision.Keep -> keepStreak
            PhotoDecision.Remove -> removeStreak
            PhotoDecision.Later -> return null
        }
        if (streak.count != settings.suggestionThreshold) {
            return null
        }

        val suggestionType = when (decision) {
            PhotoDecision.Keep -> AlbumSuggestionType.KeepLocation
            PhotoDecision.Remove -> AlbumSuggestionType.RemoveLocation
            PhotoDecision.Later -> return null
        }
        if (
            suggestionType == AlbumSuggestionType.RemoveLocation &&
            !settings.removeLocationSuggestionsEnabled
        ) {
            return null
        }

        return albumSuggestionFor(
            idPrefix = suggestionType.name,
            type = suggestionType,
            photo = photo,
            triggeringStreakCount = streak.count,
        )
    }

    private fun albumSuggestionFor(
        idPrefix: String,
        type: AlbumSuggestionType,
        photo: PhotoItem,
        triggeringStreakCount: Int,
    ): AlbumSuggestion {
        val matchingPhotoCount = photos.count { it.locationBucket == photo.locationBucket }
        return AlbumSuggestion(
            id = "$idPrefix:${photo.locationBucket}",
            type = type,
            locationBucket = photo.locationBucket,
            triggeringStreakCount = triggeringStreakCount,
            matchingPhotoCount = minOf(settings.albumSize, matchingPhotoCount),
            recommendedAlbumName = recommendedAlbumNameFor(photo),
        )
    }

    private fun recommendedAlbumNameFor(photo: PhotoItem): String {
        return if (photo.locationBucket == UNKNOWN_LOCATION_BUCKET) {
            photo.dateTakenLabel?.let { "Photos from $it" } ?: "Photos from unknown date"
        } else {
            photo.locationBucket
        }
    }
}

enum class PhotoDecision {
    Keep,
    Remove,
    Later,
}

data class LocationDecisionStreak(
    val locationBucket: String? = null,
    val count: Int = 0,
) {
    fun advancedFor(nextLocationBucket: String): LocationDecisionStreak {
        return if (locationBucket == nextLocationBucket) {
            copy(count = count + 1)
        } else {
            LocationDecisionStreak(
                locationBucket = nextLocationBucket,
                count = 1,
            )
        }
    }
}

data class AlbumSuggestion(
    val id: String,
    val type: AlbumSuggestionType,
    val locationBucket: String,
    val triggeringStreakCount: Int,
    val matchingPhotoCount: Int,
    val recommendedAlbumName: String,
    val status: AlbumSuggestionStatus = AlbumSuggestionStatus.Pending,
)

enum class AlbumSuggestionType {
    KeepLocation,
    RemoveLocation,
}

enum class AlbumSuggestionStatus {
    Pending,
    Accepted,
    Dismissed,
}

private fun List<PhotoItem>.withUniquePhoto(photo: PhotoItem): List<PhotoItem> {
    return filterNot { it.id == photo.id } + photo
}

private fun List<PhotoItem>.withoutPhoto(photo: PhotoItem): List<PhotoItem> {
    return filterNot { it.id == photo.id }
}

private fun List<AlbumSuggestion>.upsert(suggestion: AlbumSuggestion): List<AlbumSuggestion> {
    return filterNot { it.id == suggestion.id } + suggestion
}

private fun AlbumSuggestion.isAllowedBy(settings: AppSettings): Boolean {
    return settings.albumSuggestionsEnabled &&
        (type != AlbumSuggestionType.RemoveLocation || settings.removeLocationSuggestionsEnabled)
}
