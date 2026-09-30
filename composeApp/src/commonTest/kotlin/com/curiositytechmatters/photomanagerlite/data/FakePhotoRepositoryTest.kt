package com.curiositytechmatters.photomanagerlite.data

import com.curiositytechmatters.photomanagerlite.model.PhotoSourceAlbumSelection
import com.curiositytechmatters.photomanagerlite.model.PhotoSourceSettings
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakePhotoRepositoryTest {
    @Test
    fun fakeRepositoryExposesAndAppliesSourceAlbumFilters() {
        val repository = FakePhotoRepository()
        val fakeAlbum = runSuspend { repository.getSourceAlbums() }.single()

        assertEquals("Fake Photos", fakeAlbum.name)
        assertEquals(10, fakeAlbum.photoCount)

        runSuspend {
            repository.configurePhotoSource(
                PhotoSourceSettings(
                    includeSourceAlbum = PhotoSourceAlbumSelection.specific(fakeAlbum),
                )
            )
        }

        assertEquals("fake-portrait-01", runSuspend { repository.getNextPhoto() }?.id)
    }

    @Test
    fun fakeRepositoryReturnsNoPhotosWhenTheOnlyAlbumIsExcluded() {
        val repository = FakePhotoRepository()
        val fakeAlbum = runSuspend { repository.getSourceAlbums() }.single()

        runSuspend {
            repository.configurePhotoSource(
                PhotoSourceSettings(
                    excludeSourceAlbum = PhotoSourceAlbumSelection.specific(fakeAlbum),
                )
            )
        }

        assertNull(runSuspend { repository.getNextPhoto() })
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var value: T? = null
    var failure: Throwable? = null

    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                result
                    .onSuccess { value = it }
                    .onFailure { failure = it }
            }
        }
    )

    failure?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return value as T
}
