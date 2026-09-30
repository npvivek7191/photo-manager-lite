package com.curiositytechmatters.photomanagerlite.model

data class PhotoItem(
    val id: String,
    val resourcePath: String? = null,
    val contentUri: String? = null,
    val sourceAlbumId: String? = null,
    val sourceAlbumName: String? = null,
    val locationBucket: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val displayName: String? = null,
    val dateTakenMillis: Long? = null,
    val dateTakenLabel: String? = null,
)
