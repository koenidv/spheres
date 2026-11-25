@file:OptIn(ExperimentalWasmJsInterop::class)

package de.koenidv.spheres.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ---- JS interop helpers ----

private fun jsNow(): Double = js("Date.now()")
private fun jsUuid(): String = js("crypto.randomUUID()")
private fun jsStorageGet(key: String): String? = js("window.localStorage.getItem(key)")
private fun jsStorageSet(key: String, value: String): Unit = js("window.localStorage.setItem(key, value)")
private fun jsStorageRemove(key: String): Unit = js("window.localStorage.removeItem(key)")
private fun jsOpen(url: String): Unit = js("window.open(url, '_blank', 'noopener')")

private fun jsDownload(filename: String, content: String, mime: String): Unit = js(
    """{
        var blob = new Blob([content], { type: mime });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url; a.download = filename;
        document.body.appendChild(a); a.click(); a.remove();
        setTimeout(function() { URL.revokeObjectURL(url); }, 1000);
    }"""
)

private fun jsPickFile(onBytes: (JsAny) -> Unit): Unit = js(
    """{
        var input = document.createElement('input');
        input.type = 'file'; input.accept = 'image/*'; input.style.display = 'none';
        input.onchange = function() {
            var f = input.files && input.files[0];
            if (!f) { input.remove(); return; }
            var r = new FileReader();
            r.onload = function() { onBytes(new Uint8Array(r.result)); input.remove(); };
            r.readAsArrayBuffer(f);
        };
        document.body.appendChild(input); input.click();
    }"""
)

private fun jsArrayLength(a: JsAny): Int = js("a.length")
private fun jsArrayGet(a: JsAny, i: Int): Int = js("a[i]")

private fun jsLocation(onOk: (Double, Double, Double) -> Unit, onErr: () -> Unit): Unit = js(
    """{
        if (!navigator.geolocation) { onErr(); return; }
        navigator.geolocation.getCurrentPosition(
            function(p) { onOk(p.coords.latitude, p.coords.longitude, p.coords.accuracy); },
            function() { onErr(); },
            { enableHighAccuracy: false, timeout: 15000 });
    }"""
)

private fun jsGoogleSignIn(clientId: String, onToken: (String) -> Unit, onError: (String) -> Unit): Unit = js(
    """{
        if (!window.google || !google.accounts || !google.accounts.id) {
            onError('Google Sign-In could not be loaded. Check your connection or ad blocker.');
            return;
        }
        google.accounts.id.initialize({
            client_id: clientId,
            callback: function(r) { r.credential ? onToken(r.credential) : onError('Sign-in failed'); },
            use_fedcm_for_prompt: true
        });
        google.accounts.id.prompt(function(n) {
            if (n.isNotDisplayed() || n.isSkippedMoment()) {
                onError('Google Sign-In was dismissed or blocked by the browser');
            }
        });
    }"""
)

// ---- Core ----

actual val isWebPlatform: Boolean = true

actual fun haptic(durationMs: Int) {}
actual fun currentTimeMillis(): Long = jsNow().toLong()
actual fun randomUuid(): String = jsUuid()

actual fun sha256Hex(input: String): String = Sha256.hex(input.encodeToByteArray())

private object Sha256 {
    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0x0bf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    fun hex(data: ByteArray): String {
        var h0 = 0x6a09e667; var h1 = -0x4498517b; var h2 = 0x3c6ef372; var h3 = -0x5ab00ac6
        var h4 = 0x510e527f; var h5 = -0x64fa9774; var h6 = 0x1f83d9ab; var h7 = 0x5be0cd19
        val bitLen = data.size.toLong() * 8
        val padded = ByteArray(((data.size + 9 + 63) / 64) * 64)
        data.copyInto(padded)
        padded[data.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLen ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (chunk in 0 until padded.size / 64) {
            for (i in 0 until 16) {
                val j = chunk * 64 + i * 4
                w[i] = ((padded[j].toInt() and 0xff) shl 24) or ((padded[j + 1].toInt() and 0xff) shl 16) or
                    ((padded[j + 2].toInt() and 0xff) shl 8) or (padded[j + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3; var e = h4; var f = h5; var g = h6; var h = h7
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h
        }
        return intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).joinToString("") {
            it.toUInt().toString(16).padStart(8, '0')
        }
    }
}

actual object PlatformStorage {
    actual fun getString(key: String): String? = jsStorageGet(key)
    actual fun putString(key: String, value: String?) {
        if (value == null) jsStorageRemove(key) else jsStorageSet(key, value)
    }
}

actual object PlatformPhotoCache {
    private const val MAX = 200
    private val map = LinkedHashMap<String, ByteArray>()
    actual fun get(key: String): ByteArray? {
        val v = map.remove(key) ?: return null
        map[key] = v
        return v
    }
    actual fun put(key: String, bytes: ByteArray) {
        map.remove(key)
        map[key] = bytes
        while (map.size > MAX) map.remove(map.keys.first())
    }
    actual fun delete(key: String) { map.remove(key) }
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Throwable) {
    null
}

actual fun cropSquareAvatarJpeg(
    sourceBytes: ByteArray,
    leftFrac: Float,
    topFrac: Float,
    widthFrac: Float,
    heightFrac: Float,
): ByteArray? = try {
    val image = Image.makeFromEncoded(sourceBytes)
    val w = image.width.coerceAtLeast(1)
    val h = image.height.coerceAtLeast(1)
    val rawW = (widthFrac * w).toInt().coerceIn(1, w)
    val rawH = (heightFrac * h).toInt().coerceIn(1, h)
    val side = minOf(rawW, rawH)
    val srcX = (leftFrac * w).toInt().coerceIn(0, (w - side).coerceAtLeast(0))
    val srcY = (topFrac * h).toInt().coerceIn(0, (h - side).coerceAtLeast(0))
    val target = minOf(side, 512)
    val surface = Surface.makeRasterN32Premul(target, target)
    surface.canvas.drawImageRect(
        image,
        Rect.makeXYWH(srcX.toFloat(), srcY.toFloat(), side.toFloat(), side.toFloat()),
        Rect.makeWH(target.toFloat(), target.toFloat()),
    )
    surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 85)?.bytes
} catch (_: Throwable) {
    null
}

// ---- Location ----

actual object PlatformLocation {
    actual fun isSupported(): Boolean = true
    actual fun isGranted(): Boolean = false
    actual suspend fun requestPermission(): Boolean = getCurrentLocation() != null
    actual suspend fun getCurrentLocation(): UserLocation? = suspendCancellableCoroutine { cont ->
        jsLocation(
            onOk = { lat, lng, acc ->
                if (cont.isActive) cont.resume(UserLocation(lat, lng, acc.toFloat(), currentTimeMillis()))
            },
            onErr = { if (cont.isActive) cont.resume(null) },
        )
    }
}

actual object PlatformNotifications {
    actual fun isSupported(): Boolean = false
    actual fun isGranted(): Boolean = false
    actual suspend fun requestPermission(): Boolean = false
    actual fun syncSchedule(items: List<PlatformReminderItem>, overdueCount: Int) {}
}

actual object PlatformPhoneIntegration {
    actual fun isSupported(): Boolean = false
    actual fun isGranted(): Boolean = false
    actual suspend fun requestPermission(): Boolean = false
    actual fun fetchCallsLongerThan(minDurationSeconds: Long, sinceMillis: Long): List<PhoneCallRecord> = emptyList()
    actual fun getPhoneNumbersForContact(contactId: String): List<String> = emptyList()
}

actual object PlatformBilling {
    actual fun initialize(onEntitlementVerified: (verified: Boolean, platform: String?, purchaseToken: String?) -> Unit) {}
    actual suspend fun queryProduct(productId: String): BillingProductInfo =
        BillingProductInfo(productId, "", isStoreConnected = false)
    actual suspend fun purchaseUnlimited(productId: String): PurchaseOutcome =
        PurchaseOutcome.Error("Purchases are only available in the iOS and Android apps")
    actual suspend fun restorePurchases(productId: String): RestoreOutcome = RestoreOutcome.NotFound
}

// ---- Sharing / auth ----

actual fun shareJsonFile(filename: String, jsonContent: String) =
    jsDownload(filename, jsonContent, "application/json")

actual fun openExternalUrl(url: String) = jsOpen(url)

actual suspend fun launchGoogleAuthFlow(authUrl: String, callbackScheme: String): String =
    throw UnsupportedOperationException("Not used on web")

actual suspend fun signInWithGoogleNative(serverClientId: String): String? =
    suspendCancellableCoroutine { cont ->
        jsGoogleSignIn(
            clientId = serverClientId,
            onToken = { if (cont.isActive) cont.resume(it) },
            onError = { if (cont.isActive) cont.resumeWithException(Exception(it)) },
        )
    }

actual fun isAppleSignInSupported(): Boolean = false

actual suspend fun signInWithAppleNative(): AppleSignInCredential =
    throw UnsupportedOperationException("Sign in with Apple is not available on web yet")

actual fun appleLogoBitmap(): ImageBitmap? = null

// ---- Composables ----

@Composable
actual fun rememberContactPickerLauncher(
    allowMultiple: Boolean,
    onPicked: (List<PickedContact>) -> Unit,
): () -> Unit = remember { {} }

@Composable
actual fun rememberPhotoPickerLauncher(onPicked: (ByteArray) -> Unit): () -> Unit = remember(onPicked) {
    {
        jsPickFile { arr ->
            val n = jsArrayLength(arr)
            val bytes = ByteArray(n) { jsArrayGet(arr, it).toByte() }
            onPicked(bytes)
        }
    }
}

@Composable
actual fun StatusBarEffect(colorHex: Long, dimmed: Boolean) {}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}
