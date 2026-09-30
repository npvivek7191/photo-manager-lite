package com.curiositytechmatters.photomanagerlite.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.curiositytechmatters.photomanagerlite.model.AppSettings
import com.curiositytechmatters.photomanagerlite.model.NotificationFrequency
import com.curiositytechmatters.photomanagerlite.model.PhotoDecision
import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import com.curiositytechmatters.photomanagerlite.model.PhotoSortOrder
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceAlbumSelection
import com.curiositytechmatters.photomanagerlite.model.PreferredNotificationStartTime
import com.curiositytechmatters.photomanagerlite.model.WeeklyReviewDay
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAppStorageInstrumentedTest {
    private lateinit var context: Context

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearStorage()
    }

    @AfterTest
    fun tearDown() {
        clearStorage()
    }

    @Test
    fun settingsRoundTripThroughSharedPreferences() {
        val storage = AndroidAppStorage(context)
        val settings = AppSettings(
            includeSourceAlbum = PhotoSourceAlbumSelection(
                albumId = "camera",
                albumName = "Camera",
            ),
            excludeSourceAlbum = PhotoSourceAlbumSelection.None,
            photoSortOrder = PhotoSortOrder.Oldest,
            notificationsEnabled = false,
            notificationFrequency = NotificationFrequency.Weekly,
            preferredNotificationStartTime = PreferredNotificationStartTime(hour = 22, minute = 45),
            weeklyReviewDay = WeeklyReviewDay.Sunday,
            confirmBeforeRemove = false,
            albumSuggestionsEnabled = false,
            removeLocationSuggestionsEnabled = false,
            suggestionThreshold = 7,
            albumSize = 80,
            locationBucketRadiusKm = 10.0,
        )

        runBlockingForTest {
            storage.saveSettings(settings)
            assertEquals(settings, storage.loadSettings())
        }
    }

    @Test
    fun decisionRoundTripStoresStablePhotoMetadata() {
        val storage = AndroidAppStorage(context)
        val photo = PhotoItem(
            id = "media:42",
            contentUri = "content://media/external/images/media/42",
            sourceAlbumId = "camera",
            sourceAlbumName = "Camera",
            locationBucket = "Photos from 2026-07-19",
            latitude = 59.3293,
            longitude = 18.0686,
            displayName = "IMG_0042.jpg",
            dateTakenMillis = 1_785_000_000_000L,
            dateTakenLabel = "2026-07-19",
        )

        runBlockingForTest {
            storage.saveDecision(
                photo = photo,
                decision = PhotoDecision.Later,
            )

            val record = storage.loadDecisions().single()
            assertEquals(photo.id, record.photoId)
            assertEquals(photo.contentUri, record.contentUri)
            assertEquals(photo.displayName, record.displayName)
            assertEquals(photo.dateTakenMillis, record.dateTakenMillis)
            assertEquals(photo.dateTakenLabel, record.dateTakenLabel)
            assertEquals(photo.locationBucket, record.locationBucket)
            assertEquals(photo.latitude, record.latitude)
            assertEquals(photo.longitude, record.longitude)
            assertEquals(StoredPhotoDecision.Skipped, record.decision)
            assertTrue(record.decidedAtMillis > 0L)
        }
    }

    private fun clearStorage() {
        context.getSharedPreferences(
            "photo_manager_lite_storage",
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}

private fun <T> runBlockingForTest(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    var value: T? = null
    var failure: Throwable? = null

    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                result
                    .onSuccess { value = it }
                    .onFailure { failure = it }
                latch.countDown()
            }
        }
    )

    assertTrue(latch.await(5, TimeUnit.SECONDS))
    failure?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return value as T
}
