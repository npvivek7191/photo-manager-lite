package com.curiositytechmatters.photomanagerlite.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

const val UNKNOWN_LOCATION_BUCKET = "Unknown location"

private const val KM_PER_LATITUDE_DEGREE = 111.32

/**
 * Applies the selected location radius to every photo that has GPS coordinates.
 */
fun List<PhotoItem>.withLocationBucketRadius(radiusKm: Double): List<PhotoItem> {
    return map { photo -> photo.withLocationBucketRadius(radiusKm) }
}

/**
 * Rebuilds a photo's location bucket from GPS coordinates and the selected radius.
 *
 * Fake photos keep their authored location names because they do not have raw GPS metadata.
 */
fun PhotoItem.withLocationBucketRadius(radiusKm: Double): PhotoItem {
    val photoLatitude = latitude ?: return this
    val photoLongitude = longitude ?: return this

    return copy(
        locationBucket = locationBucketForCoordinates(
            latitude = photoLatitude,
            longitude = photoLongitude,
            radiusKm = radiusKm,
        )
    )
}

/**
 * Converts exact GPS coordinates into an approximate location bucket for matching streaks.
 */
fun locationBucketForCoordinates(
    latitude: Double,
    longitude: Double,
    radiusKm: Double,
): String {
    val safeRadiusKm = radiusKm.coerceAtLeast(0.1)
    val latitudeStep = safeRadiusKm / KM_PER_LATITUDE_DEGREE
    val longitudeKmPerDegree = KM_PER_LATITUDE_DEGREE * max(
        0.01,
        abs(cos(latitude.toRadians())),
    )
    val longitudeStep = safeRadiusKm / longitudeKmPerDegree
    val displayDecimals = displayDecimalsFor(safeRadiusKm)
    val latitudeBucket = bucketCoordinate(latitude, latitudeStep).roundedTo(displayDecimals)
    val longitudeBucket = bucketCoordinate(longitude, longitudeStep).roundedTo(displayDecimals)

    return "Near $latitudeBucket, $longitudeBucket"
}

/**
 * Returns user-facing text for the location radius setting.
 */
fun locationBucketRadiusLabel(radiusKm: Double): String {
    return when (radiusKm) {
        0.1 -> "0.1 km (100 m) - For too many clicks"
        1.0 -> "1 km - Too many clicks only on vacation"
        10.0 -> "10 km - For a few clicks"
        100.0 -> "100 km - For rare clicks"
        else -> "${radiusKm.roundedTo(decimals = 1)} km"
    }
}

private fun bucketCoordinate(value: Double, step: Double): Double {
    return round(value / step) * step
}

private fun displayDecimalsFor(radiusKm: Double): Int {
    return when {
        radiusKm <= 0.1 -> 4
        radiusKm <= 1.0 -> 3
        radiusKm <= 10.0 -> 2
        else -> 1
    }
}

private fun Double.roundedTo(decimals: Int): Double {
    val scale = 10.0.pow(decimals)
    return round(this * scale) / scale
}

private fun Double.toRadians(): Double {
    return this * PI / 180.0
}
