package com.curiositytechmatters.photomanagerlite

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Size
import com.curiositytechmatters.photomanagerlite.data.AndroidAppStorage
import com.curiositytechmatters.photomanagerlite.data.AndroidPhotoRepository
import com.curiositytechmatters.photomanagerlite.data.StoredPhotoDecision
import com.curiositytechmatters.photomanagerlite.model.AppSettings
import com.curiositytechmatters.photomanagerlite.model.NotificationFrequency
import com.curiositytechmatters.photomanagerlite.model.PhotoDecision
import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import com.curiositytechmatters.photomanagerlite.model.WeeklyReviewDay
import com.curiositytechmatters.photomanagerlite.model.photoSourceSettings
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PhotoReviewNotificationContract {
    const val ACTION_SHOW_REVIEW =
        "com.curiositytechmatters.photomanagerlite.action.SHOW_REVIEW_NOTIFICATION"
    const val ACTION_KEEP_PHOTO =
        "com.curiositytechmatters.photomanagerlite.action.KEEP_NOTIFICATION_PHOTO"
    const val ACTION_REMOVE_PHOTO =
        "com.curiositytechmatters.photomanagerlite.action.REMOVE_NOTIFICATION_PHOTO"

    private const val EXTRA_PHOTO_ID = "extra_photo_id"
    private const val EXTRA_CONTENT_URI = "extra_content_uri"
    private const val EXTRA_SOURCE_ALBUM_ID = "extra_source_album_id"
    private const val EXTRA_SOURCE_ALBUM_NAME = "extra_source_album_name"
    private const val EXTRA_LOCATION_BUCKET = "extra_location_bucket"
    private const val EXTRA_LATITUDE = "extra_latitude"
    private const val EXTRA_LONGITUDE = "extra_longitude"
    private const val EXTRA_DISPLAY_NAME = "extra_display_name"
    private const val EXTRA_DATE_TAKEN_MILLIS = "extra_date_taken_millis"
    private const val EXTRA_DATE_TAKEN_LABEL = "extra_date_taken_label"

    fun putPhotoExtras(
        intent: Intent,
        photo: PhotoItem,
    ): Intent {
        intent.putExtra(EXTRA_PHOTO_ID, photo.id)
        intent.putExtra(EXTRA_CONTENT_URI, photo.contentUri)
        intent.putExtra(EXTRA_SOURCE_ALBUM_ID, photo.sourceAlbumId)
        intent.putExtra(EXTRA_SOURCE_ALBUM_NAME, photo.sourceAlbumName)
        intent.putExtra(EXTRA_LOCATION_BUCKET, photo.locationBucket)
        photo.latitude?.let { intent.putExtra(EXTRA_LATITUDE, it) }
        photo.longitude?.let { intent.putExtra(EXTRA_LONGITUDE, it) }
        intent.putExtra(EXTRA_DISPLAY_NAME, photo.displayName)
        photo.dateTakenMillis?.let { intent.putExtra(EXTRA_DATE_TAKEN_MILLIS, it) }
        intent.putExtra(EXTRA_DATE_TAKEN_LABEL, photo.dateTakenLabel)
        return intent
    }

    fun notificationRemoveActionFrom(intent: Intent?): NotificationPhotoAction? {
        if (intent?.action != ACTION_REMOVE_PHOTO) {
            return null
        }

        return photoFrom(intent)?.let { photo ->
            NotificationPhotoAction(photo = photo)
        }
    }

    internal fun photoFrom(intent: Intent): PhotoItem? {
        val photoId = intent.getStringExtra(EXTRA_PHOTO_ID) ?: return null
        val locationBucket = intent.getStringExtra(EXTRA_LOCATION_BUCKET) ?: return null

        return PhotoItem(
            id = photoId,
            contentUri = intent.getStringExtra(EXTRA_CONTENT_URI),
            sourceAlbumId = intent.getStringExtra(EXTRA_SOURCE_ALBUM_ID),
            sourceAlbumName = intent.getStringExtra(EXTRA_SOURCE_ALBUM_NAME),
            locationBucket = locationBucket,
            latitude = intent.nullableDouble(EXTRA_LATITUDE),
            longitude = intent.nullableDouble(EXTRA_LONGITUDE),
            displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME),
            dateTakenMillis = intent.nullableLong(EXTRA_DATE_TAKEN_MILLIS),
            dateTakenLabel = intent.getStringExtra(EXTRA_DATE_TAKEN_LABEL),
        )
    }
}

data class NotificationPhotoAction(
    val photo: PhotoItem,
)

object PhotoReviewNotificationScheduler {
    private const val CHANNEL_ID = "photo_review"
    private const val CHANNEL_NAME = "Photo review"
    private const val NOTIFICATION_ID = 1001
    private const val SCHEDULE_REQUEST_CODE = 2001
    private const val OPEN_APP_REQUEST_CODE = 2002
    private const val KEEP_REQUEST_CODE = 2003
    private const val REMOVE_REQUEST_CODE = 2004
    private const val MAX_NOTIFICATION_SCAN_COUNT = 250
    private const val NOTIFICATION_IMAGE_SIZE_PX = 1024

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = context.notificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Photo review reminders with Keep and Remove actions"
        }

        notificationManager.createNotificationChannel(channel)
    }

    fun scheduleNext(
        context: Context,
        settings: AppSettings,
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val alarmIntent = reviewAlarmPendingIntent(appContext)

        alarmManager.cancel(alarmIntent)
        if (!settings.notificationsEnabled || !appContext.hasNotificationPermission()) {
            cancelVisibleNotification(appContext)
            return
        }

        ensureChannel(appContext)
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            nextTriggerAtMillis(settings = settings),
            alarmIntent,
        )
    }

    fun cancelVisibleNotification(context: Context) {
        context.notificationManager.cancel(NOTIFICATION_ID)
    }

    internal suspend fun showNextReviewNotification(context: Context) {
        val appContext = context.applicationContext
        val storage = AndroidAppStorage(appContext)
        val settings = storage.loadSettings()

        if (!settings.notificationsEnabled || !appContext.hasNotificationPermission()) {
            scheduleNext(appContext, settings)
            return
        }

        val nextPhoto = nextNotificationPhoto(
            context = appContext,
            settings = settings,
            storage = storage,
        )
        if (nextPhoto != null) {
            ensureChannel(appContext)
            appContext.notificationManager.notify(
                NOTIFICATION_ID,
                buildReviewNotification(
                    context = appContext,
                    photo = nextPhoto,
                ),
            )
        }

        scheduleNext(appContext, settings)
    }

    internal suspend fun keepNotificationPhoto(
        context: Context,
        photo: PhotoItem,
    ) {
        val appContext = context.applicationContext
        val storage = AndroidAppStorage(appContext)
        AndroidPhotoRepository(appContext).keepPhoto(photo)
        storage.saveDecision(
            photo = photo,
            decision = PhotoDecision.Keep,
        )
        cancelVisibleNotification(appContext)
        scheduleNext(
            context = appContext,
            settings = storage.loadSettings(),
        )
    }

    private suspend fun nextNotificationPhoto(
        context: Context,
        settings: AppSettings,
        storage: AndroidAppStorage,
    ): PhotoItem? {
        val hiddenPhotoIds = storage.loadDecisions()
            .asSequence()
            .filter {
                it.decision == StoredPhotoDecision.Keep ||
                    it.decision == StoredPhotoDecision.Remove
            }
            .map { it.photoId }
            .toSet()
        val repository = AndroidPhotoRepository(context)

        repository.configurePhotoSource(settings.photoSourceSettings)
        repeat(MAX_NOTIFICATION_SCAN_COUNT) {
            val candidate = repository.getNextPhoto() ?: return null
            if (candidate.id !in hiddenPhotoIds) {
                return candidate
            }
        }

        return null
    }

    private fun buildReviewNotification(
        context: Context,
        photo: PhotoItem,
    ): Notification {
        val photoBitmap = context.loadNotificationBitmap(
            photo = photo,
            maxSizePx = NOTIFICATION_IMAGE_SIZE_PX,
        )
        val title = "Review this photo"
        val body = photo.displayName ?: photo.sourceAlbumName ?: photo.locationBucket
        val builder = notificationBuilder(context)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openAppPendingIntent(context))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(Notification.PRIORITY_DEFAULT)
            .addAction(
                android.R.drawable.ic_menu_save,
                "Keep",
                keepPendingIntent(
                    context = context,
                    photo = photo,
                ),
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                "Remove",
                removePendingIntent(
                    context = context,
                    photo = photo,
                ),
            )

        if (photoBitmap != null) {
            builder
                .setLargeIcon(photoBitmap)
                .setStyle(
                    Notification.BigPictureStyle()
                        .bigPicture(photoBitmap)
                        .setSummaryText(body)
                )
        }

        return builder.build()
    }

    @Suppress("DEPRECATION")
    private fun notificationBuilder(context: Context): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
    }

    private fun reviewAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PhotoReviewNotificationReceiver::class.java).apply {
            action = PhotoReviewNotificationContract.ACTION_SHOW_REVIEW
        }

        return PendingIntent.getBroadcast(
            context,
            SCHEDULE_REQUEST_CODE,
            intent,
            pendingIntentFlags(),
        )
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            pendingIntentFlags(),
        )
    }

    private fun keepPendingIntent(
        context: Context,
        photo: PhotoItem,
    ): PendingIntent {
        val intent = Intent(context, PhotoReviewNotificationReceiver::class.java).apply {
            action = PhotoReviewNotificationContract.ACTION_KEEP_PHOTO
            PhotoReviewNotificationContract.putPhotoExtras(this, photo)
        }

        return PendingIntent.getBroadcast(
            context,
            KEEP_REQUEST_CODE,
            intent,
            pendingIntentFlags(),
        )
    }

    private fun removePendingIntent(
        context: Context,
        photo: PhotoItem,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = PhotoReviewNotificationContract.ACTION_REMOVE_PHOTO
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            PhotoReviewNotificationContract.putPhotoExtras(this, photo)
        }

        return PendingIntent.getActivity(
            context,
            REMOVE_REQUEST_CODE,
            intent,
            pendingIntentFlags(),
        )
    }
}

class PhotoReviewNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    PhotoReviewNotificationContract.ACTION_SHOW_REVIEW ->
                        PhotoReviewNotificationScheduler.showNextReviewNotification(context)
                    PhotoReviewNotificationContract.ACTION_KEEP_PHOTO ->
                        PhotoReviewNotificationContract.photoFrom(intent)?.let { photo ->
                            PhotoReviewNotificationScheduler.keepNotificationPhoto(
                                context = context,
                                photo = photo,
                            )
                        }
                    Intent.ACTION_BOOT_COMPLETED -> {
                        val storage = AndroidAppStorage(context.applicationContext)
                        PhotoReviewNotificationScheduler.scheduleNext(
                            context = context,
                            settings = storage.loadSettings(),
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal fun nextTriggerAtMillis(
    nowMillis: Long = System.currentTimeMillis(),
    settings: AppSettings,
): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.HOUR_OF_DAY, settings.preferredNotificationStartTime.hour)
        set(Calendar.MINUTE, settings.preferredNotificationStartTime.minute)
    }

    if (settings.notificationFrequency == NotificationFrequency.Weekly) {
        calendar.set(Calendar.DAY_OF_WEEK, settings.weeklyReviewDay.toCalendarDay())
    }

    val stepField = when (settings.notificationFrequency) {
        NotificationFrequency.TwelveHours -> Calendar.HOUR_OF_DAY
        NotificationFrequency.Daily,
        NotificationFrequency.AlternateDays -> Calendar.DAY_OF_YEAR
        NotificationFrequency.Weekly -> Calendar.WEEK_OF_YEAR
    }
    val stepAmount = when (settings.notificationFrequency) {
        NotificationFrequency.TwelveHours -> 12
        NotificationFrequency.Daily -> 1
        NotificationFrequency.AlternateDays -> 2
        NotificationFrequency.Weekly -> 1
    }

    while (calendar.timeInMillis <= nowMillis) {
        calendar.add(stepField, stepAmount)
    }

    return calendar.timeInMillis
}

private fun WeeklyReviewDay.toCalendarDay(): Int {
    return when (this) {
        WeeklyReviewDay.Monday -> Calendar.MONDAY
        WeeklyReviewDay.Tuesday -> Calendar.TUESDAY
        WeeklyReviewDay.Wednesday -> Calendar.WEDNESDAY
        WeeklyReviewDay.Thursday -> Calendar.THURSDAY
        WeeklyReviewDay.Friday -> Calendar.FRIDAY
        WeeklyReviewDay.Saturday -> Calendar.SATURDAY
        WeeklyReviewDay.Sunday -> Calendar.SUNDAY
    }
}

private val Context.notificationManager: NotificationManager
    get() = getSystemService(NotificationManager::class.java)

private fun Context.hasNotificationPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

@Suppress("DEPRECATION")
private fun Context.loadNotificationBitmap(
    photo: PhotoItem,
    maxSizePx: Int,
): Bitmap? {
    val contentUri = photo.contentUri?.let(Uri::parse) ?: return null

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.loadThumbnail(
                contentUri,
                Size(maxSizePx, maxSizePx),
                null,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(contentResolver, contentUri)
            ) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val scale = maxOf(1, maxOf(width, height) / maxSizePx)
                decoder.setTargetSize(
                    width / scale,
                    height / scale,
                )
            }
        } else {
            contentResolver.openInputStream(contentUri)?.use(BitmapFactory::decodeStream)
        }
    } catch (_: Exception) {
        null
    }
}

private fun Intent.nullableDouble(name: String): Double? {
    return if (hasExtra(name)) getDoubleExtra(name, 0.0) else null
}

private fun Intent.nullableLong(name: String): Long? {
    return if (hasExtra(name)) getLongExtra(name, 0L) else null
}

private fun pendingIntentFlags(): Int {
    return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
