package com.curiositytechmatters.photomanagerlite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.curiositytechmatters.photomanagerlite.PhotoImage
import com.curiositytechmatters.photomanagerlite.model.AlbumSuggestion
import com.curiositytechmatters.photomanagerlite.model.AlbumSuggestionType
import com.curiositytechmatters.photomanagerlite.model.AppState
import com.curiositytechmatters.photomanagerlite.model.LocationDecisionStreak
import com.curiositytechmatters.photomanagerlite.model.PhotoDecision
import com.curiositytechmatters.photomanagerlite.model.PhotoItem
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    state: AppState,
    actionsEnabled: Boolean = true,
    onOpenSettings: () -> Unit,
    onReviewPhoto: (PhotoDecision) -> Unit,
    onOpenAlbumSuggestion: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onAcceptSuggestion: (String?) -> Unit,
) {
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }
    val currentPhoto = state.currentPhoto

    fun requestRemoveDecision() {
        if (!actionsEnabled) {
            return
        }
        if (state.settings.confirmBeforeRemove) {
            showRemoveConfirmDialog = true
        } else {
            onReviewPhoto(PhotoDecision.Remove)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ReviewHeader(onOpenSettings = onOpenSettings)
            Spacer(modifier = Modifier.height(12.dp))
            ReviewSummary(
                keptCount = state.keptPhotos.size,
                removedCount = state.removedPhotos.size,
                laterCount = state.skippedPhotos.size,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (currentPhoto == null) {
                    EmptyReviewState()
                } else {
                    SwipeablePhotoCard(
                        photo = currentPhoto,
                        enabled = actionsEnabled,
                        onSwipeLeft = { requestRemoveDecision() },
                        onSwipeRight = { onReviewPhoto(PhotoDecision.Keep) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            ReviewActionRow(
                enabled = currentPhoto != null && actionsEnabled,
                albumEnabled = currentPhoto != null &&
                    actionsEnabled &&
                    state.settings.albumSuggestionsEnabled,
                onRemove = { requestRemoveDecision() },
                onLater = { onReviewPhoto(PhotoDecision.Later) },
                onAlbum = onOpenAlbumSuggestion,
                onKeep = { onReviewPhoto(PhotoDecision.Keep) },
            )
            Spacer(modifier = Modifier.height(18.dp))
            ReviewStreakSummary(
                keepStreak = state.currentKeepStreak,
                removeStreak = state.currentRemoveStreak,
            )
        }
    }

    state.activeAlbumSuggestion?.let { suggestion ->
        AlbumSuggestionDialog(
            suggestion = suggestion,
            onDismiss = onDismissSuggestion,
            onAccept = onAcceptSuggestion,
        )
    }

    if (showRemoveConfirmDialog && currentPhoto != null) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmDialog = false },
            title = {
                Text("Remove this photo?")
            },
            text = {
                Text("Android may ask you to confirm moving this photo to trash.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirmDialog = false
                        onReviewPhoto(PhotoDecision.Remove)
                    }
                ) {
                    Text(
                        text = "Remove",
                        color = MaterialTheme.colors.secondary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ReviewHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Photo Manager Lite",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Open settings",
                tint = MaterialTheme.colors.onBackground,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun ReviewSummary(
    keptCount: Int,
    removedCount: Int,
    laterCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CounterText(label = "Keep", value = keptCount, modifier = Modifier.weight(1f))
        CounterText(label = "Remove", value = removedCount, modifier = Modifier.weight(1f))
        CounterText(label = "Later", value = laterCount, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReviewStreakSummary(
    keepStreak: LocationDecisionStreak,
    removeStreak: LocationDecisionStreak,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StreakText(label = "Keep streak", streak = keepStreak, modifier = Modifier.weight(1f))
        StreakText(label = "Remove streak", streak = removeStreak, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CounterText(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(8.dp),
        elevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.64f),
            )
        }
    }
}

@Composable
private fun StreakText(
    label: String,
    streak: LocationDecisionStreak,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(8.dp),
        elevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = streak.count.toString(),
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.64f),
            )
            Text(
                text = streak.locationBucket ?: "None",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.58f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyReviewState() {
    Text(
        text = "No photos to review",
        style = MaterialTheme.typography.body1,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f),
    )
}

@Composable
private fun SwipeablePhotoCard(
    photo: PhotoItem,
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    var dragOffset by remember(photo.id) { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 120.dp.toPx() }

    Card(
        modifier = Modifier
            .widthIn(max = 430.dp)
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .offset(y = 50.dp)
            .offset { IntOffset(dragOffset.roundToInt(), 0) }
            .graphicsLayer {
                rotationZ = dragOffset / 42f
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(photo.id) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.x
                            },
                            onDragCancel = { dragOffset = 0f },
                            onDragEnd = {
                                when {
                                    dragOffset <= -swipeThreshold -> onSwipeLeft()
                                    dragOffset >= swipeThreshold -> onSwipeRight()
                                }
                                dragOffset = 0f
                            },
                        )
                    }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(14.dp),
        elevation = 10.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PhotoImage(
                photo = photo,
                contentDescription = photo.id,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            LocationBadge(
                locationName = photo.locationBucket,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 15.dp, end = 14.dp),
            )
        }
    }
}

@Composable
private fun LocationBadge(
    locationName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = locationName,
            style = MaterialTheme.typography.body2,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ReviewActionRow(
    enabled: Boolean,
    albumEnabled: Boolean,
    onRemove: () -> Unit,
    onLater: () -> Unit,
    onAlbum: () -> Unit,
    onKeep: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReviewActionButton(
            icon = Icons.Default.Close,
            iconTint = Color(0xFFD32F2F),
            label = "Remove",
            enabled = enabled,
            onClick = onRemove,
        )
        ReviewActionButton(
            icon = Icons.Default.Timer,
            iconTint = MaterialTheme.colors.onSurface,
            label = "Later",
            enabled = enabled,
            onClick = onLater,
        )
        ReviewActionButton(
            icon = Icons.Default.Style,
            iconTint = MaterialTheme.colors.onSurface,
            label = "Album",
            enabled = albumEnabled,
            onClick = onAlbum,
        )
        ReviewActionButton(
            icon = Icons.Default.Check,
            iconTint = Color(0xFF2E7D32),
            label = "Keep",
            enabled = enabled,
            onClick = onKeep,
        )
    }
}

@Composable
private fun ReviewActionButton(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(68.dp)
                .clickable(enabled = enabled, onClick = onClick),
            shape = CircleShape,
            color = MaterialTheme.colors.surface,
            elevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint.copy(alpha = if (enabled) 1f else 0.32f),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onBackground.copy(alpha = if (enabled) 1f else 0.42f),
        )
    }
}

@Composable
private fun AlbumSuggestionDialog(
    suggestion: AlbumSuggestion,
    onDismiss: () -> Unit,
    onAccept: (String?) -> Unit,
) {
    var albumName by remember(suggestion.id) { mutableStateOf(suggestion.recommendedAlbumName) }
    val isKeepSuggestion = suggestion.type == AlbumSuggestionType.KeepLocation
    val isManualSuggestion = suggestion.triggeringStreakCount == 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isKeepSuggestion) {
                    "Save similar photos?"
                } else {
                    "Review similar removals?"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isKeepSuggestion) {
                        if (isManualSuggestion) {
                            "Save a rule for ${suggestion.matchingPhotoCount} matching photos from ${suggestion.locationBucket}?"
                        } else {
                            "You kept ${suggestion.triggeringStreakCount} in a row from ${suggestion.locationBucket}. Save a rule for ${suggestion.matchingPhotoCount} matching photos?"
                        }
                    } else {
                        "You removed ${suggestion.triggeringStreakCount} in a row from ${suggestion.locationBucket}. Save this location for future remove review?"
                    }
                )
                if (isKeepSuggestion) {
                    TextField(
                        value = albumName,
                        onValueChange = { albumName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Album name") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAccept(if (isKeepSuggestion) albumName else null)
                }
            ) {
                Text(
                    text = if (isKeepSuggestion) {
                        "Save rule"
                    } else {
                        "Save review"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        },
    )
}
