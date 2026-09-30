package com.curiositytechmatters.photomanagerlite

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.curiositytechmatters.photomanagerlite.data.AndroidAppStorage
import com.curiositytechmatters.photomanagerlite.data.AndroidPhotoRepository
import com.curiositytechmatters.photomanagerlite.data.FakePhotoRepository
import com.curiositytechmatters.photomanagerlite.model.PhotoDecision
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var pendingNotificationAction by mutableStateOf<NotificationPhotoAction?>(null)

    /**
     * Creates repository instances, asks for photo access, and launches the shared Compose app.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationAction = PhotoReviewNotificationContract.notificationRemoveActionFrom(
            intent
        )
        PhotoReviewNotificationScheduler.ensureChannel(applicationContext)
        setContent {
            val fakePhotoRepository = remember {
                FakePhotoRepository()
            }
            val appStorage = remember {
                AndroidAppStorage(applicationContext)
            }
            var completeTrashRequest by remember {
                mutableStateOf<((Boolean) -> Unit)?>(null)
            }
            val trashLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                completeTrashRequest?.invoke(result.resultCode == Activity.RESULT_OK)
                completeTrashRequest = null
            }
            val trashRequester: suspend (IntentSender) -> Boolean = remember(trashLauncher) {
                { intentSender ->
                    suspendCancellableCoroutine { continuation ->
                        completeTrashRequest = { didTrash ->
                            if (continuation.isActive) {
                                continuation.resume(didTrash)
                            }
                        }
                        continuation.invokeOnCancellation {
                            completeTrashRequest = null
                        }
                        try {
                            trashLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        } catch (_: Exception) {
                            completeTrashRequest = null
                            if (continuation.isActive) {
                                continuation.resume(false)
                            }
                        }
                    }
                }
            }
            val androidPhotoRepository = remember(trashRequester) {
                AndroidPhotoRepository(
                    context = applicationContext,
                    trashRequester = trashRequester,
                )
            }
            var hasPhotoPermission by remember {
                mutableStateOf(hasPhotoReadPermission())
            }
            var hasNotificationAccess by remember {
                mutableStateOf(hasNotificationPermission())
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                hasPhotoPermission = hasPhotoReadPermission()
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                hasNotificationAccess = hasNotificationPermission()
            }

            LaunchedEffect(Unit) {
                if (!hasPhotoPermission) {
                    permissionLauncher.launch(photoPermissionsToRequest())
                }
                if (!hasNotificationAccess && Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(appStorage, hasNotificationAccess) {
                PhotoReviewNotificationScheduler.scheduleNext(
                    context = applicationContext,
                    settings = appStorage.loadSettings(),
                )
            }

            val notificationAction = pendingNotificationAction
            LaunchedEffect(notificationAction, hasPhotoPermission) {
                val action = notificationAction ?: return@LaunchedEffect
                if (!hasPhotoPermission) {
                    return@LaunchedEffect
                }

                val didRemove = androidPhotoRepository.requestRemovePhoto(action.photo)
                pendingNotificationAction = null
                if (didRemove) {
                    appStorage.saveDecision(
                        photo = action.photo,
                        decision = PhotoDecision.Remove,
                    )
                    PhotoReviewNotificationScheduler.cancelVisibleNotification(applicationContext)
                    PhotoReviewNotificationScheduler.scheduleNext(
                        context = applicationContext,
                        settings = appStorage.loadSettings(),
                    )
                }
            }

            App(
                // Use fake photos until permission is granted so the app remains usable.
                photoRepository = if (hasPhotoPermission) {
                    androidPhotoRepository
                } else {
                    fakePhotoRepository
                },
                appStorage = appStorage,
                onSettingsSaved = { settings ->
                    PhotoReviewNotificationScheduler.scheduleNext(
                        context = applicationContext,
                        settings = settings,
                    )
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationAction = PhotoReviewNotificationContract.notificationRemoveActionFrom(
            intent
        )
    }

    /**
     * Checks whether this Android version has any usable read access for images.
     */
    private fun hasPhotoReadPermission(): Boolean {
        return photoReadPermissions().any { permission ->
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns the correct image-read permission set for the current Android version.
     */
    private fun photoReadPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Returns every permission needed for real-photo loading and location metadata access.
     *
     * ACCESS_MEDIA_LOCATION is requested separately from read access because location metadata is
     * redacted on newer Android versions unless the app has explicit permission.
     */
    private fun photoPermissionsToRequest(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            photoReadPermissions() + Manifest.permission.ACCESS_MEDIA_LOCATION
        } else {
            photoReadPermissions()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}

@Composable
@Preview
fun AppPreview() {
    App()
}
