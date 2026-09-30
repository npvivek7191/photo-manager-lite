package com.curiositytechmatters.photomanagerlite.data

import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStorageTest {
    @Test
    fun keepAndRemoveDecisionsAreExcludedFromFutureReviewQueues() {
        val photos = listOf(
            PhotoItem(id = "kept", locationBucket = "Stockholm"),
            PhotoItem(id = "removed", locationBucket = "Stockholm"),
            PhotoItem(id = "skipped", locationBucket = "Stockholm"),
            PhotoItem(id = "new", locationBucket = "Stockholm"),
        )
        val decisions = listOf(
            decisionRecord(photoId = "kept", decision = StoredPhotoDecision.Keep),
            decisionRecord(photoId = "removed", decision = StoredPhotoDecision.Remove),
            decisionRecord(photoId = "skipped", decision = StoredPhotoDecision.Skipped),
        )

        val visiblePhotos = photos.withoutStoredKeepOrRemoveDecisions(decisions)

        assertEquals(listOf("skipped", "new"), visiblePhotos.map { it.id })
    }

    private fun decisionRecord(
        photoId: String,
        decision: StoredPhotoDecision,
    ) = PhotoDecisionRecord(
        photoId = photoId,
        contentUri = null,
        displayName = null,
        dateTakenMillis = null,
        dateTakenLabel = null,
        locationBucket = "Stockholm",
        latitude = null,
        longitude = null,
        decision = decision,
        decidedAtMillis = 0L,
    )
}
