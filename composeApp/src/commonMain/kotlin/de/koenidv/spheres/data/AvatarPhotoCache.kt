package de.koenidv.spheres.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import de.koenidv.spheres.platform.PlatformPhotoCache
import de.koenidv.spheres.platform.decodeImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object AvatarPhotoCache {
    private val bitmaps = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<ImageBitmap?>>()

    fun seed(url: String, bytes: ByteArray, decoded: ImageBitmap? = null) {
        PlatformPhotoCache.put(url, bytes)
        val bmp = decoded ?: decodeImageBitmap(bytes) ?: return
        bitmaps.update { it + (url to bmp) }
    }

    fun remove(url: String) {
        PlatformPhotoCache.delete(url)
        bitmaps.update { it - url }
    }

    suspend fun getOrLoad(url: String): ImageBitmap? {
        bitmaps.value[url]?.let { return it }

        var owner = false
        val deferred = mutex.withLock {
            bitmaps.value[url]?.let { return it }
            val existing = inFlight[url]
            if (existing != null) {
                existing
            } else {
                owner = true
                CompletableDeferred<ImageBitmap?>().also { inFlight[url] = it }
            }
        }

        if (!owner) return deferred.await()

        var result: ImageBitmap? = null
        try {
            result = withContext(Dispatchers.Default) {
                val cachedBytes = PlatformPhotoCache.get(url)
                if (cachedBytes != null) {
                    decodeImageBitmap(cachedBytes)
                } else {
                    val downloaded = SpheresStore.downloadPhotoBytes(url)
                    if (downloaded != null) {
                        PlatformPhotoCache.put(url, downloaded)
                        decodeImageBitmap(downloaded)
                    } else {
                        null
                    }
                }
            }
            if (result != null) {
                bitmaps.update { it + (url to result) }
            }
            deferred.complete(result)
            return result
        } catch (t: Throwable) {
            deferred.complete(null)
            return null
        } finally {
            mutex.withLock {
                if (inFlight[url] === deferred) {
                    inFlight.remove(url)
                }
            }
        }
    }

    @Composable
    fun rememberPhotoBitmap(photoUrl: String?): ImageBitmap? {
        if (photoUrl.isNullOrBlank()) return null
        val map by bitmaps.collectAsState()
        val cached = map[photoUrl]
        LaunchedEffect(photoUrl, cached == null) {
            if (cached == null) {
                getOrLoad(photoUrl)
            }
        }
        return cached
    }
}
