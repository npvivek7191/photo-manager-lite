package com.curiositytechmatters.photomanagerlite

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoReviewNotificationContractInstrumentedTest {
    @Test
    fun removeActionRoundTripsPhotoExtras() {
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
        val intent = Intent().apply {
            action = PhotoReviewNotificationContract.ACTION_REMOVE_PHOTO
            PhotoReviewNotificationContract.putPhotoExtras(this, photo)
        }

        val action = PhotoReviewNotificationContract.notificationRemoveActionFrom(intent)

        assertEquals(photo, action?.photo)
    }

    @Test
    fun nonRemoveNotificationIntentIsIgnored() {
        val intent = Intent().apply {
            action = PhotoReviewNotificationContract.ACTION_KEEP_PHOTO
        }

        assertNull(PhotoReviewNotificationContract.notificationRemoveActionFrom(intent))
    }
}
