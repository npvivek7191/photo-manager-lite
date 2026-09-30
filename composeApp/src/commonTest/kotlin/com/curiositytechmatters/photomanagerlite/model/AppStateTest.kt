package com.curiositytechmatters.photomanagerlite.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppStateTest {
    @Test
    fun keepStreakCreatesDateBasedAlbumNameWhenLocationIsUnknown() {
        val state = AppState(
            photos = listOf(
                unknownLocationPhoto(id = "one"),
                unknownLocationPhoto(id = "two"),
                unknownLocationPhoto(id = "three"),
            ),
            settings = AppSettings(suggestionThreshold = 3),
        )

        val afterThreeKeeps = state
            .reviewCurrentPhoto(PhotoDecision.Keep)
            .reviewCurrentPhoto(PhotoDecision.Keep)
            .reviewCurrentPhoto(PhotoDecision.Keep)

        val suggestion = assertNotNull(afterThreeKeeps.activeAlbumSuggestion)
        assertEquals(AlbumSuggestionType.KeepLocation, suggestion.type)
        assertEquals(UNKNOWN_LOCATION_BUCKET, suggestion.locationBucket)
        assertEquals("Photos from 2026-07-19", suggestion.recommendedAlbumName)
        assertEquals(3, suggestion.triggeringStreakCount)
    }

    @Test
    fun dateBasedLocationBucketIsKeptAsRecommendedAlbumName() {
        val state = AppState(
            photos = listOf(
                dateBucketPhoto(id = "one"),
                dateBucketPhoto(id = "two"),
                dateBucketPhoto(id = "three"),
            ),
            settings = AppSettings(suggestionThreshold = 3),
        )

        val afterThreeKeeps = state
            .reviewCurrentPhoto(PhotoDecision.Keep)
            .reviewCurrentPhoto(PhotoDecision.Keep)
            .reviewCurrentPhoto(PhotoDecision.Keep)

        val suggestion = assertNotNull(afterThreeKeeps.activeAlbumSuggestion)
        assertEquals("Photos from 2026-07-19", suggestion.locationBucket)
        assertEquals("Photos from 2026-07-19", suggestion.recommendedAlbumName)
    }

    @Test
    fun disabledAlbumSuggestionsBlockManualAndAutomaticSuggestions() {
        val state = AppState(
            photos = listOf(
                knownLocationPhoto(id = "one"),
                knownLocationPhoto(id = "two"),
                knownLocationPhoto(id = "three"),
            ),
            settings = AppSettings(
                suggestionThreshold = 3,
                albumSuggestionsEnabled = false,
            ),
        )

        assertNull(state.requestAlbumSuggestionForCurrentPhoto().activeAlbumSuggestion)

        val afterThreeKeeps = state
            .reviewCurrentPhoto(PhotoDecision.Keep)
            .reviewCurrentPhoto(PhotoDecision.Keep)
            .reviewCurrentPhoto(PhotoDecision.Keep)

        assertNull(afterThreeKeeps.activeAlbumSuggestion)
        assertEquals(emptyList(), afterThreeKeeps.albumSuggestions)
    }

    @Test
    fun removeLocationSuggestionsCanBeDisabledSeparately() {
        val state = AppState(
            photos = listOf(
                knownLocationPhoto(id = "one"),
                knownLocationPhoto(id = "two"),
                knownLocationPhoto(id = "three"),
            ),
            settings = AppSettings(
                suggestionThreshold = 3,
                removeLocationSuggestionsEnabled = false,
            ),
        )

        val afterThreeRemoves = state
            .reviewCurrentPhoto(PhotoDecision.Remove)
            .reviewCurrentPhoto(PhotoDecision.Remove)
            .reviewCurrentPhoto(PhotoDecision.Remove)

        assertNull(afterThreeRemoves.activeAlbumSuggestion)
        assertEquals(emptyList(), afterThreeRemoves.albumSuggestions)
    }

    private fun unknownLocationPhoto(id: String) = PhotoItem(
        id = id,
        locationBucket = UNKNOWN_LOCATION_BUCKET,
        dateTakenLabel = "2026-07-19",
    )

    private fun dateBucketPhoto(id: String) = PhotoItem(
        id = id,
        locationBucket = "Photos from 2026-07-19",
        dateTakenLabel = "2026-07-19",
    )

    private fun knownLocationPhoto(id: String) = PhotoItem(
        id = id,
        locationBucket = "Stockholm Old Town",
    )
}
