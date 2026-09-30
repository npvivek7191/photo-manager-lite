package com.curiositytechmatters.photomanagerlite

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.curiositytechmatters.photomanagerlite.data.AppStorage
import com.curiositytechmatters.photomanagerlite.data.FakePhotoRepository
import com.curiositytechmatters.photomanagerlite.data.NoOpAppStorage
import com.curiositytechmatters.photomanagerlite.data.PhotoRepository
import com.curiositytechmatters.photomanagerlite.data.withoutStoredKeepOrRemoveDecisions
import com.curiositytechmatters.photomanagerlite.model.AppSettings
import com.curiositytechmatters.photomanagerlite.model.AppState
import com.curiositytechmatters.photomanagerlite.model.PhotoDecision
import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceAlbum
import com.curiositytechmatters.photomanagerlite.model.photoSourceSettings
import com.curiositytechmatters.photomanagerlite.ui.MainScreen
import com.curiositytechmatters.photomanagerlite.ui.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Root Compose entry point for the shared app UI.
 *
 * The Android entry point can inject a real MediaStore-backed repository, while previews and
 * non-Android targets can continue using the fake repository.
 */
@Composable
fun App(
    photoRepository: PhotoRepository? = null,
    appStorage: AppStorage? = null,
    onSettingsSaved: suspend (AppSettings) -> Unit = {},
) {
    val resolvedPhotoRepository = photoRepository ?: remember { FakePhotoRepository() }
    val resolvedAppStorage = appStorage ?: remember { NoOpAppStorage() }
    val coroutineScope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var appState by remember { mutableStateOf(AppState()) }
    var sourceAlbums by remember { mutableStateOf(emptyList<PhotoSourceAlbum>()) }
    var isRemoveInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(resolvedPhotoRepository, resolvedAppStorage) {
        val storedSettings = resolvedAppStorage.loadSettings()
        val storedDecisions = resolvedAppStorage.loadDecisions()
        sourceAlbums = resolvedPhotoRepository.getSourceAlbums()
        val repositoryPhotos = resolvedPhotoRepository.loadInitialPhotos(storedSettings)
            .withoutStoredKeepOrRemoveDecisions(storedDecisions)

        // Keep the UI state repository-agnostic by converting stored data into AppState.
        appState = appState
            .withSettings(storedSettings)
            .withPhotos(repositoryPhotos)
        onSettingsSaved(storedSettings)
    }

    MaterialTheme(colors = appColors(isDarkTheme = isSystemInDarkTheme())) {
        if (showSettings) {
            SettingsScreen(
                settings = appState.settings,
                sourceAlbums = sourceAlbums,
                onSettingsChange = { newSettings ->
                    val previousSettings = appState.settings
                    val photoSourceChanged = previousSettings.photoSourceSettings !=
                        newSettings.photoSourceSettings

                    appState = appState.withSettings(newSettings)
                    coroutineScope.launch {
                        resolvedAppStorage.saveSettings(newSettings)
                        onSettingsSaved(newSettings)
                        if (photoSourceChanged) {
                            sourceAlbums = resolvedPhotoRepository.getSourceAlbums()
                            val storedDecisions = resolvedAppStorage.loadDecisions()
                            val repositoryPhotos = resolvedPhotoRepository
                                .loadInitialPhotos(newSettings)
                                .withoutStoredKeepOrRemoveDecisions(storedDecisions)

                            appState = appState
                                .withSettings(newSettings)
                                .withPhotos(repositoryPhotos)
                        }
                    }
                },
                onBack = { showSettings = false },
            )
        } else {
            MainScreen(
                state = appState,
                actionsEnabled = !isRemoveInProgress,
                onOpenSettings = { showSettings = true },
                onReviewPhoto = { decision ->
                    val reviewedPhoto = appState.currentPhoto
                    if (reviewedPhoto != null) {
                        when (decision) {
                            PhotoDecision.Keep,
                            PhotoDecision.Later -> {
                                appState = appState.reviewCurrentPhoto(decision)
                                coroutineScope.launch {
                                    val didRecord = resolvedPhotoRepository.recordDecision(
                                        photo = reviewedPhoto,
                                        decision = decision,
                                    )
                                    if (didRecord) {
                                        resolvedAppStorage.saveDecision(
                                            photo = reviewedPhoto,
                                            decision = decision,
                                        )
                                    }
                                }
                            }
                            PhotoDecision.Remove -> {
                                if (!isRemoveInProgress) {
                                    coroutineScope.launch {
                                        isRemoveInProgress = true
                                        try {
                                            val didRemove = resolvedPhotoRepository.recordDecision(
                                                photo = reviewedPhoto,
                                                decision = decision,
                                            )
                                            if (
                                                didRemove &&
                                                appState.currentPhoto?.id == reviewedPhoto.id
                                            ) {
                                                appState = appState.reviewCurrentPhoto(decision)
                                                resolvedAppStorage.saveDecision(
                                                    photo = reviewedPhoto,
                                                    decision = decision,
                                                )
                                            }
                                        } finally {
                                            isRemoveInProgress = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                onOpenAlbumSuggestion = {
                    appState = appState.requestAlbumSuggestionForCurrentPhoto()
                },
                onDismissSuggestion = { appState = appState.dismissActiveAlbumSuggestion() },
                onAcceptSuggestion = { appState = appState.acceptActiveAlbumSuggestion(it) },
            )
        }
    }
}

/**
 * Pulls an initial batch of unique photos from the repository for the in-memory review flow.
 */
private suspend fun PhotoRepository.loadInitialPhotos(
    settings: AppSettings,
    maxPhotos: Int = 250,
): List<PhotoItem> {
    val photos = mutableListOf<PhotoItem>()
    val seenPhotoIds = mutableSetOf<String>()

    configurePhotoSource(settings.photoSourceSettings)

    while (photos.size < maxPhotos) {
        val nextPhoto = getNextPhoto() ?: break
        if (!seenPhotoIds.add(nextPhoto.id)) {
            break
        }
        photos += nextPhoto
    }

    return photos
}

/**
 * Sends the user's review decision back to the repository boundary.
 *
 * Remove returns false when Android's system trash confirmation is canceled or unavailable.
 */
private suspend fun PhotoRepository.recordDecision(
    photo: PhotoItem,
    decision: PhotoDecision,
): Boolean {
    return when (decision) {
        PhotoDecision.Keep -> {
            keepPhoto(photo)
            true
        }
        PhotoDecision.Remove -> requestRemovePhoto(photo)
        PhotoDecision.Later -> true
    }
}

private fun appColors(isDarkTheme: Boolean) = if (isDarkTheme) {
    darkColors(
        primary = Color(0xFF6FCF97),
        primaryVariant = Color(0xFF3BA86A),
        secondary = Color(0xFFFF6B5F),
        background = Color(0xFF101412),
        surface = Color(0xFF1B211F),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
    )
} else {
    lightColors(
        primary = Color(0xFF1F7A6D),
        primaryVariant = Color(0xFF15574E),
        secondary = Color(0xFFD32F2F),
        background = Color(0xFFF5F1EA),
        surface = Color.White,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF1E2523),
        onSurface = Color(0xFF1E2523),
    )
}
