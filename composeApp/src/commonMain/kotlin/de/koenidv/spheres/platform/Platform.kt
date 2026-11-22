package de.koenidv.spheres.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import de.koenidv.spheres.domain.Birthday
import kotlinx.serialization.Serializable

@Serializable
data class PickedContact(
    val name: String,
    val contactId: String? = null,
    val phoneNumbers: List<String> = emptyList(),
    val city: String? = null,
    val address: String? = null,
    val country: String? = null,
    val birthday: Birthday? = null,
)

@Serializable
data class PlatformReminderItem(
    val id: String,
    val title: String,
    val body: String,
    val at: Long,
    val personId: String,
)

@Serializable
data class PhoneCallRecord(
    val id: String,
    val number: String? = null,
    val contactId: String? = null,
    val contactName: String? = null,
    val startedAt: Long,
    val durationSeconds: Long,
)

data class AppleSignInCredential(
    val idToken: String,
    val rawNonce: String,
    val fullName: String? = null,
    val authorizationCode: String? = null,
)

@Serializable
data class BillingProductInfo(
    val productId: String,
    val formattedPrice: String,
    val isStoreConnected: Boolean = false,
)

sealed interface PurchaseOutcome {
    data class Success(
        val productId: String,
        val platform: String? = null,
        val purchaseToken: String? = null,
    ) : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Error(val message: String) : PurchaseOutcome
}

sealed interface RestoreOutcome {
    data class Found(val platform: String, val purchaseToken: String? = null) : RestoreOutcome
    data object NotFound : RestoreOutcome
}

expect object PlatformBilling {
    fun initialize(onEntitlementVerified: (verified: Boolean, platform: String?, purchaseToken: String?) -> Unit)
    suspend fun queryProduct(productId: String): BillingProductInfo
    suspend fun purchaseUnlimited(productId: String): PurchaseOutcome
    suspend fun restorePurchases(productId: String): RestoreOutcome
}

expect fun haptic(durationMs: Int = 8)
expect fun currentTimeMillis(): Long
expect fun randomUuid(): String
expect fun sha256Hex(input: String): String

expect object PlatformStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

expect object PlatformPhotoCache {
    fun get(key: String): ByteArray?
    fun put(key: String, bytes: ByteArray)
    fun delete(key: String)
}

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

expect fun cropSquareAvatarJpeg(
    sourceBytes: ByteArray,
    leftFrac: Float,
    topFrac: Float,
    widthFrac: Float,
    heightFrac: Float,
): ByteArray?

@Serializable
data class UserLocation(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float? = null,
    val updatedAt: Long = 0L,
)

expect object PlatformLocation {
    fun isSupported(): Boolean
    fun isGranted(): Boolean
    suspend fun requestPermission(): Boolean
    suspend fun getCurrentLocation(): UserLocation?
}

expect object PlatformNotifications {
    fun isSupported(): Boolean
    fun isGranted(): Boolean
    suspend fun requestPermission(): Boolean
    fun syncSchedule(items: List<PlatformReminderItem>, overdueCount: Int)
}

expect object PlatformPhoneIntegration {
    fun isSupported(): Boolean
    fun isGranted(): Boolean
    suspend fun requestPermission(): Boolean
    fun fetchCallsLongerThan(minDurationSeconds: Long, sinceMillis: Long): List<PhoneCallRecord>
    fun getPhoneNumbersForContact(contactId: String): List<String>
}

expect fun shareJsonFile(filename: String, jsonContent: String)
expect fun openExternalUrl(url: String)
expect suspend fun launchGoogleAuthFlow(authUrl: String, callbackScheme: String): String
expect suspend fun signInWithGoogleNative(serverClientId: String): String?
expect fun isAppleSignInSupported(): Boolean
expect suspend fun signInWithAppleNative(): AppleSignInCredential

/** Apple's official logo glyph for the Sign in with Apple button, null where unavailable. */
expect fun appleLogoBitmap(): ImageBitmap?

@Composable
expect fun rememberContactPickerLauncher(
    allowMultiple: Boolean = true,
    onPicked: (List<PickedContact>) -> Unit,
): () -> Unit

@Composable
expect fun rememberPhotoPickerLauncher(onPicked: (ByteArray) -> Unit): () -> Unit

@Composable
expect fun StatusBarEffect(colorHex: Long, dimmed: Boolean)

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

enum class NotificationActionType { CHECKIN, SNOOZE }

object NotificationActionBridge {
    private var handler: ((personId: String, action: NotificationActionType) -> Unit)? = null
    private const val PENDING_KEY = "pending_notification_actions"

    fun registerHandler(onAction: (personId: String, action: NotificationActionType) -> Unit) {
        handler = onAction
        drainPending()
    }

    fun dispatch(personId: String, action: NotificationActionType) {
        val active = handler
        if (active != null) {
            active(personId, action)
        } else {
            val existing = PlatformStorage.getString(PENDING_KEY).orEmpty()
            val entry = "$personId:${action.name}"
            val updated = if (existing.isBlank()) entry else "$existing\n$entry"
            PlatformStorage.putString(PENDING_KEY, updated)
        }
    }

    fun drainPending() {
        val active = handler ?: return
        val raw = PlatformStorage.getString(PENDING_KEY) ?: return
        PlatformStorage.putString(PENDING_KEY, null)
        raw.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val action = NotificationActionType.entries.firstOrNull { it.name == parts[1] } ?: return@forEach
                active(parts[0], action)
            }
        }
    }
}

@Serializable
data class UnlinkedCallPrompt(
    val contactId: String,
    val contactName: String,
    val durationMinutes: Long,
    val callStartedAt: Long,
)

object PostHogConstants {
    const val API_KEY: String = "phc_vQHABLdS6yd3pMEtFDJi9sbSKdBr9B4aYqdLvUVNRjFu"
    const val HOST: String = "https://eu.i.posthog.com"
}

object PlatformAnalytics {
    fun capture(event: String, properties: Map<String, Any> = emptyMap()) {
        com.posthog.kmp.PostHog.capture(
            event = event,
            properties = properties.ifEmpty { null }
        )
    }

    fun screen(screenTitle: String, properties: Map<String, Any> = emptyMap()) {
        com.posthog.kmp.PostHog.screen(
            screenName = screenTitle,
            properties = properties.ifEmpty { null }
        )
    }

    fun identify(distinctId: String, userProperties: Map<String, Any> = emptyMap()) {
        com.posthog.kmp.PostHog.identify(
            distinctId = distinctId,
            userProperties = userProperties.ifEmpty { null }
        )
    }

    fun reset() {
        com.posthog.kmp.PostHog.reset()
    }
}
