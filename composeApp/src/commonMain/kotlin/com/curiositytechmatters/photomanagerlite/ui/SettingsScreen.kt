package com.curiositytechmatters.photomanagerlite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.curiositytechmatters.photomanagerlite.model.AppSettings
import com.curiositytechmatters.photomanagerlite.model.NotificationFrequency
import com.curiositytechmatters.photomanagerlite.model.PhotoSortOrder
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceAlbum
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceAlbumSelection
import com.curiositytechmatters.photomanagerlite.model.PreferredNotificationStartTime
import com.curiositytechmatters.photomanagerlite.model.WeeklyReviewDay
import com.curiositytechmatters.photomanagerlite.model.locationBucketRadiusKmOptions
import com.curiositytechmatters.photomanagerlite.model.locationBucketRadiusLabel

@Composable
fun SettingsScreen(
    settings: AppSettings,
    sourceAlbums: List<PhotoSourceAlbum> = emptyList(),
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var showPreferredStartTimePicker by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Done")
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            SettingsGroup(title = "Photo Source") {
                val includeEnabled = !settings.excludeSourceAlbum.isSpecificAlbum
                val excludeEnabled = !settings.includeSourceAlbum.isSpecificAlbum

                AlbumDropdownRow(
                    label = "Include",
                    defaultOptionLabel = "All",
                    selectedLabel = settings.includeSourceAlbum.label(defaultLabel = "All"),
                    sourceAlbums = sourceAlbums,
                    enabled = includeEnabled,
                    onSelectDefault = {
                        onSettingsChange(
                            settings.copy(includeSourceAlbum = PhotoSourceAlbumSelection.All)
                        )
                    },
                    onSelectAlbum = { album ->
                        onSettingsChange(
                            settings.copy(
                                includeSourceAlbum = PhotoSourceAlbumSelection.specific(album),
                                excludeSourceAlbum = PhotoSourceAlbumSelection.None,
                            )
                        )
                    },
                )
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                AlbumDropdownRow(
                    label = "Exclude",
                    defaultOptionLabel = "None",
                    selectedLabel = settings.excludeSourceAlbum.label(defaultLabel = "None"),
                    sourceAlbums = sourceAlbums,
                    enabled = excludeEnabled,
                    onSelectDefault = {
                        onSettingsChange(
                            settings.copy(excludeSourceAlbum = PhotoSourceAlbumSelection.None)
                        )
                    },
                    onSelectAlbum = { album ->
                        onSettingsChange(
                            settings.copy(
                                includeSourceAlbum = PhotoSourceAlbumSelection.All,
                                excludeSourceAlbum = PhotoSourceAlbumSelection.specific(album),
                            )
                        )
                    },
                )
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.body1,
                )
                photoSortOrderOptions.forEach { sortOrder ->
                    SelectionButton(
                        text = sortOrder.label,
                        isSelected = settings.photoSortOrder == sortOrder,
                        onClick = {
                            onSettingsChange(settings.copy(photoSortOrder = sortOrder))
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            SettingsGroup(title = "Review") {
                SettingRow(label = "Enable notifications") {
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(notificationsEnabled = it))
                        },
                    )
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                SettingRow(
                    label = "Preferred notification start time",
                    enabled = settings.notificationsEnabled,
                ) {
                    Button(
                        onClick = { showPreferredStartTimePicker = true },
                        enabled = settings.notificationsEnabled,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(settings.preferredNotificationStartTime.label)
                    }
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                notificationFrequencyOptions.forEach { frequency ->
                    SelectionButton(
                        text = frequency.label,
                        isSelected = settings.notificationFrequency == frequency,
                        enabled = settings.notificationsEnabled,
                        onClick = {
                            onSettingsChange(settings.copy(notificationFrequency = frequency))
                        },
                    )
                }
                if (settings.notificationFrequency == NotificationFrequency.Weekly) {
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                    Text(
                        text = "Preferred day",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.alpha(if (settings.notificationsEnabled) 1f else 0.48f),
                    )
                    weeklyReviewDayOptions.forEach { reviewDay ->
                        SelectionButton(
                            text = reviewDay.label,
                            isSelected = settings.weeklyReviewDay == reviewDay,
                            enabled = settings.notificationsEnabled,
                            onClick = {
                                onSettingsChange(settings.copy(weeklyReviewDay = reviewDay))
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            SettingsGroup(title = "Remove") {
                SettingRow(label = "Move to trash") {
                    Text(
                        text = "Enabled",
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                SettingRow(label = "Confirm before remove") {
                    Switch(
                        checked = settings.confirmBeforeRemove,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(confirmBeforeRemove = it))
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            SettingsGroup(title = "Album Suggestions") {
                SettingRow(label = "Enable album suggestions") {
                    Switch(
                        checked = settings.albumSuggestionsEnabled,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(albumSuggestionsEnabled = it))
                        },
                    )
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                StepperRow(
                    label = "Suggestion threshold",
                    value = settings.suggestionThreshold,
                    enabled = settings.albumSuggestionsEnabled,
                    canDecrement = settings.suggestionThreshold > 3,
                    canIncrement = settings.suggestionThreshold < 10,
                    onDecrement = {
                        onSettingsChange(
                            settings.copy(suggestionThreshold = settings.suggestionThreshold - 1)
                        )
                    },
                    onIncrement = {
                        onSettingsChange(
                            settings.copy(suggestionThreshold = settings.suggestionThreshold + 1)
                        )
                    },
                )
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                StepperRow(
                    label = "Album size",
                    value = settings.albumSize,
                    enabled = settings.albumSuggestionsEnabled,
                    canDecrement = settings.albumSize > 10,
                    canIncrement = settings.albumSize < 100,
                    onDecrement = {
                        onSettingsChange(settings.copy(albumSize = settings.albumSize - 10))
                    },
                    onIncrement = {
                        onSettingsChange(settings.copy(albumSize = settings.albumSize + 10))
                    },
                )
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                Text(
                    text = "Location grouping radius",
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.alpha(if (settings.albumSuggestionsEnabled) 1f else 0.48f),
                )
                locationBucketRadiusKmOptions.forEach { radiusKm ->
                    SelectionButton(
                        text = locationBucketRadiusLabel(radiusKm),
                        isSelected = settings.locationBucketRadiusKm == radiusKm,
                        enabled = settings.albumSuggestionsEnabled,
                        onClick = {
                            onSettingsChange(settings.copy(locationBucketRadiusKm = radiusKm))
                        },
                    )
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                SettingRow(
                    label = "Enable remove-location suggestions",
                    enabled = settings.albumSuggestionsEnabled,
                ) {
                    Switch(
                        checked = settings.removeLocationSuggestionsEnabled,
                        enabled = settings.albumSuggestionsEnabled,
                        onCheckedChange = {
                            onSettingsChange(
                                settings.copy(removeLocationSuggestionsEnabled = it)
                            )
                        },
                    )
                }
            }
        }
    }

    if (showPreferredStartTimePicker) {
        PreferredNotificationStartTimeDialog(
            currentTime = settings.preferredNotificationStartTime,
            onDismiss = { showPreferredStartTimePicker = false },
            onSave = { nextStartTime ->
                showPreferredStartTimePicker = false
                onSettingsChange(
                    settings.copy(preferredNotificationStartTime = nextStartTime)
                )
            },
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface,
            shape = RoundedCornerShape(8.dp),
            elevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun AlbumDropdownRow(
    label: String,
    defaultOptionLabel: String,
    selectedLabel: String,
    sourceAlbums: List<PhotoSourceAlbum>,
    enabled: Boolean,
    onSelectDefault: () -> Unit,
    onSelectAlbum: (PhotoSourceAlbum) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    SettingRow(
        label = label,
        enabled = enabled,
    ) {
        Box {
            OutlinedButton(
                onClick = { isExpanded = true },
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(selectedLabel)
            }
            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false },
            ) {
                DropdownMenuItem(
                    onClick = {
                        isExpanded = false
                        onSelectDefault()
                    },
                ) {
                    Text(defaultOptionLabel)
                }
                sourceAlbums.forEach { album ->
                    DropdownMenuItem(
                        onClick = {
                            isExpanded = false
                            onSelectAlbum(album)
                        },
                    ) {
                        Text(album.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionButton(
    text: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = if (isSelected) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.outlinedButtonColors(
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.onSurface,
            )
        },
        elevation = ButtonDefaults.elevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        Text(text)
    }
}

private val photoSortOrderOptions = listOf(
    PhotoSortOrder.Newest,
    PhotoSortOrder.Oldest,
    PhotoSortOrder.Random,
)

private val notificationFrequencyOptions = listOf(
    NotificationFrequency.TwelveHours,
    NotificationFrequency.Daily,
    NotificationFrequency.AlternateDays,
    NotificationFrequency.Weekly,
)

private val weeklyReviewDayOptions = listOf(
    WeeklyReviewDay.Monday,
    WeeklyReviewDay.Tuesday,
    WeeklyReviewDay.Wednesday,
    WeeklyReviewDay.Thursday,
    WeeklyReviewDay.Friday,
    WeeklyReviewDay.Saturday,
    WeeklyReviewDay.Sunday,
)

@Composable
private fun PreferredNotificationStartTimeDialog(
    currentTime: PreferredNotificationStartTime,
    onDismiss: () -> Unit,
    onSave: (PreferredNotificationStartTime) -> Unit,
) {
    var selectedTime by remember(currentTime) { mutableStateOf(currentTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Preferred notification start time")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = selectedTime.label,
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                )
                StepperRow(
                    label = "Hour",
                    value = selectedTime.hour,
                    valueText = selectedTime.hour.twoDigitString(),
                    canDecrement = selectedTime.hour > 0,
                    canIncrement = selectedTime.hour < 23,
                    onDecrement = {
                        selectedTime = selectedTime.copy(hour = selectedTime.hour - 1)
                    },
                    onIncrement = {
                        selectedTime = selectedTime.copy(hour = selectedTime.hour + 1)
                    },
                )
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                StepperRow(
                    label = "Minute",
                    value = selectedTime.minute,
                    valueText = selectedTime.minute.twoDigitString(),
                    canDecrement = selectedTime.minute > 0,
                    canIncrement = selectedTime.minute < 59,
                    onDecrement = {
                        selectedTime = selectedTime.copy(minute = selectedTime.minute - 1)
                    },
                    onIncrement = {
                        selectedTime = selectedTime.copy(minute = selectedTime.minute + 1)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedTime) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SettingRow(
    label: String,
    enabled: Boolean = true,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body1,
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else 0.48f),
        )
        Box(modifier = Modifier.padding(start = 12.dp)) {
            control()
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    valueText: String = value.toString(),
    enabled: Boolean = true,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    SettingRow(
        label = label,
        enabled = enabled,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDecrement,
                enabled = enabled && canDecrement,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("-")
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(if (enabled) 1f else 0.48f),
            )
            OutlinedButton(
                onClick = onIncrement,
                enabled = enabled && canIncrement,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("+")
            }
        }
    }
}

private fun Int.twoDigitString(): String {
    return toString().padStart(length = 2, padChar = '0')
}
