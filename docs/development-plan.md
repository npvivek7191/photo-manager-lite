# Photo Manager Lite Development Plan

## App Intro

Photo Manager Lite is an Android-first photo review app. The main experience should feel like a Tinder-style photo stack: one photo is shown at a time, and the user can quickly choose whether to keep or remove it.

The app will also support lightweight settings, scheduled review notifications, and smart album suggestions. When the user keeps several photos from the same location, the app can suggest saving other photos from that location under an album-like rule with a recommended editable name.

The app should also show a photo review notification based on the user's chosen frequency. The notification contains an image preview and actions for Keep and Remove. Remove moves the photo to trash. Keep stores the unique ID of the photo on the device so the app knows that photo should not be suggested for removal again.

## Core Behavior

Notification frequency options:

- every 12 hours
- every day
- alternate days
- once a week, with the user choosing the day

Notification time setting:

- preferred notification start time, chosen by the user from a time picker

Photo review behavior:

- Keep saves the photo's unique ID on the device.
- Remove moves the photo to trash.
- After each action, the app advances to the next candidate photo.

Location suggestion behavior:

- If the user keeps the configured number of photos continuously from the same approximate location, show a popup asking whether the user wants to save other photos from that approximate location as an album-like rule.
- If the user accepts the keep-location suggestion, show another notification or prompt with a recommended album name.
- The recommended album name should be based on the location where the photos were taken.
- If the photo location is unavailable, the app should use the photo taken date as the fallback grouping bucket and recommended album name instead of `Unknown location`.
- The user must be able to edit the recommended album name.
- If the user removes the configured number of photos continuously from the same approximate location, show a popup asking whether the user wants to remove other photos from that approximate location.
- The continuous Keep/Remove threshold must be configurable in settings, with a minimum of 3 and a maximum of 10.
- The user can choose album size in settings, from 10 to 100 photos in steps of 10.

## Current Project Direction

Use the `composeApp` module as the real app module.

The old `app` module is a classic Android-only XML/AppCompat module. It does not help with the Compose Multiplatform direction and can be removed later.

For now, focus only on Android. Keep shared UI and app rules in `commonMain` when practical, and put Android photo access, deletion, notifications, and persistence in `androidMain`.

## Step 1: Build The Fake Tinder UI

Build this before touching device photos.

Main screen:

- Top: app title and settings button
- Center: large photo card
- Bottom: Remove and Keep buttons
- Gesture: swipe left to remove, swipe right to keep

Use fake photo data first. This makes the main experience testable before permissions, storage, and MediaStore are involved.

Sub-steps:

### Step 1.1: Define The Fake Photo Model

Create a small shared `PhotoItem` model for the fake UI.

Required fields:

- `id`: stable unique identifier for Keep/Remove decisions.
- `resourcePath`: bundled fake photo path, such as `fake_photos/portrait_01.png`, so the UI can load the image before real device photo URIs exist.
- `locationBucket`: approximate fake location used later for location streak and album suggestion behavior.

Optional field:

- `displayName`: useful for labels, debug output, and future mapping to real media display names, but not required for the first fake UI to work.

### Step 1.2: Add Fake Photo Data

Create fake photo data that references the bundled portrait assets.

For the current fake assets, use:

```text
fake_photos/portrait_01.png
fake_photos/portrait_02.png
fake_photos/portrait_03.png
fake_photos/portrait_04.png
fake_photos/portrait_05.png
fake_photos/portrait_06.png
fake_photos/portrait_07.png
fake_photos/portrait_08.png
fake_photos/portrait_09.png
fake_photos/portrait_10.png
```

### Step 1.3: Build The Main Review Screen

Create the fake Tinder-style main screen:

- Top: app title and settings button.
- Center: one large photo card loaded from `PhotoItem.resourcePath`.
- Bottom: Remove and Keep buttons.
- Local state: current photo index, kept count, and removed count.
- Button behavior: Keep and Remove update in-memory counts and advance to the next fake photo.

### Step 1.4: Add Swipe Decisions

Add drag/swipe behavior to the photo card:

- Swipe left past a threshold to remove.
- Swipe right past a threshold to keep.
- Reset the card position if the swipe does not pass the threshold.
- Keep the behavior in memory only.

### Step 1.5: Add The Fake Settings Screen

Create a simple settings screen backed by in-memory settings:

- Notification frequency selection.
- Preferred notification start time.
- Confirm before remove toggle.
- Continuous Keep/Remove suggestion threshold from 3 to 10.
- Album size from 10 to 100 in steps of 10.

### Step 1.6: Keep Step 1 Fake Only

Do not add these yet:

- Android permissions.
- `MediaStore`.
- Real photo deletion or trashing.
- Persistence.
- Notifications.

Suggested files:

```text
composeApp/src/commonMain/kotlin/.../ui/MainScreen.kt
composeApp/src/commonMain/kotlin/.../ui/SettingsScreen.kt
composeApp/src/commonMain/kotlin/.../model/PhotoItem.kt
composeApp/src/commonMain/kotlin/.../model/AppSettings.kt
```

## Step 2: Add App State

Add simple state for:

- current photo
- kept photos
- removed photos
- settings
- album suggestions
- current keep streak by approximate location
- current remove streak by approximate location

At this stage, Keep and Remove should only update memory. No real device trashing yet.

## Step 3: Add Android Photo Loading

Add Android-only photo scanning from `MediaStore`.

Suggested file:

```text
composeApp/src/androidMain/kotlin/.../data/AndroidPhotoRepository.kt
```

The shared code should depend on an interface, not Android APIs directly:

```kotlin
interface PhotoRepository {
    suspend fun getNextPhoto(): PhotoItem?
    suspend fun keepPhoto(photo: PhotoItem)
    suspend fun requestRemovePhoto(photo: PhotoItem)
}
```

Android can implement the real version using `MediaStore`.

## Step 4: Store Decisions And Settings

Store decisions and settings behind a shared `AppStorage` boundary.

DataStore remains a good future fit for this app because it will store settings and many photo
decisions, but the current implementation uses Android `SharedPreferences` because DataStore is
not currently wired into the project dependencies.

Store more than the filename:

- photo URI or MediaStore ID
- unique photo ID used by the device/media store
- display name
- date taken
- location bucket
- decision: keep, remove, or skipped

Do not rely only on filename. Photo names are not guaranteed to be unique.

Implementation note for this step:

- Android currently persists settings and decisions with `SharedPreferences`.
- The shared code uses the `AppStorage` interface so this can be replaced by DataStore later.
- Stored decisions include stable photo ID, content URI, display name, date taken, location bucket,
  coordinates, decision, and decision time.

## Step 5: Add Real Remove Safely

Start with moving photos to trash instead of permanent deletion.

On Android, deleting or trashing media usually requires a system confirmation through `MediaStore.createTrashRequest(...)` or `MediaStore.createDeleteRequest(...)`.

First version:

```text
Swipe/Remove button
-> ask Android to trash photo
-> if confirmed, move to the next photo
```

Implement this inside the app before adding notification actions.

## Step 6: Add Location Grouping

When a user keeps photos, group them by approximate location.

Do not compare exact latitude and longitude. Use a radius or bucket:

```text
same location = within 100m / 1km / 10km / 100km
```

Then:

```text
if continuous kept count for location bucket reaches configured threshold:
    show album suggestion dialog

if continuous removed count for location bucket reaches configured threshold:
    show remove-location suggestion dialog
```

For the first version, make the album an app-level album/rule, not a physical Android gallery album.

Example:

```text
Location bucket: Stockholm Old Town
Album name: Stockholm Old Town
Rule: auto-keep photos near this location
```

Unknown-location example:

```text
Location bucket: Photos from 2026-07-19
Album name: Photos from 2026-07-19
Rule: auto-keep photos from this date bucket until better location data is available
```

Physical gallery album management can come later.

For remove-location suggestions, start with a confirmation prompt. If the user accepts, the app should still trash photos through Android's supported media APIs and respect any system confirmation flow.

## Step 7: Add Settings Screen

Start small.

Photo Source:

- Include: Shows a (dropdown) list of all albums on the device. "All" is the default.
- Exclude: Shows a (dropdown) list of all albums on the device. "None" is the default.
- Sort by newest, oldest, or random

If anything other than "All" is chosen in Include settings, then, the Exclude setting shall be
disabled. If anything other than "None" is chosen in Exclude settings, then, the Include setting
shall be disabled.

Review:

- Enable notifications
- Notification frequency: 12 hours, daily, alternate days, or once a week
- Preferred notification start time
- Day picker for once-a-week schedules

Remove:

- Move to trash instead of permanent delete

Album Suggestions:

- Enable album suggestions
- Continuous Keep/Remove count before suggestion, configurable from 3 to 10
- Album size, configurable from 10 to 100 photos in steps of 10
- Location radius
- Enable remove-location suggestions

Implementation note for this step:

- Android source albums use MediaStore bucket IDs and display names.
- Include and Exclude are single-album filters and remain mutually exclusive in the UI.
- Source filter or sort changes reload the in-app review queue while preserving stored Keep/Remove decisions.
- Notification settings are persisted here; actual notification delivery is Step 8.

## Step 8: Add Notifications Last

Notifications should come after the in-app flow works.

Android pieces:

- `POST_NOTIFICATIONS` permission on Android 13+
- notification channel
- big image notification
- actions: Keep and Remove
- notification schedule
- schedule settings for 12 hours, daily, alternate days, and once a week
- preferred notification start time from settings

Important: notification Remove may need to open the app because Android may require a system confirmation before trashing media.

Implementation note for this step:

- The first Android implementation uses platform `AlarmManager` scheduling because WorkManager is
  not currently a project dependency.
- The notification channel and Android 13+ notification permission are wired in `androidMain`.
- The Keep action is handled from the notification and stores the Keep decision on device.
- The Remove action opens the app so the existing MediaStore trash confirmation flow can run.
- Alarms are rescheduled from saved settings and restored after device boot.

## Recommended Order

1. Remove or ignore `app`.
2. Build fake Tinder-style main screen.
3. Build settings screen with fake settings.
4. Add fake repository and swipe/Keep/Remove behavior.
5. Add Android `MediaStore` photo loading.
6. Add app storage persistence.
7. Add trash/remove flow.
8. Add location grouping and album suggestion popup.
9. Add notifications.
10. Later: add iOS implementations behind the same common interfaces.

This order keeps each step testable. The app becomes useful before the harder Android background, permission, and media-deletion parts are added.
