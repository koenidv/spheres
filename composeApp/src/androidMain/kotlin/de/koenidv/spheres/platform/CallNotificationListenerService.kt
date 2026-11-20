package de.koenidv.spheres.platform

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Detects the duration of calls (phone, WhatsApp, Telegram, ...) from the ongoing-call
 * notification every calling app shows while a call is running.
 *
 * Privacy contract, mirrored in the in-app disclosure:
 * - does nothing unless the user enabled the phone integration in Spheres
 * - only looks at notifications flagged as an ongoing call (Notification.CATEGORY_CALL)
 * - only reads the notification title (the caller's name or number), never message text
 * - keeps nothing but caller, start time and duration, on this device only
 */
class CallNotificationListenerService : NotificationListenerService() {

    private class ActiveCall(
        val title: String?,
        var startedAt: Long,
        var connected: Boolean,
    )

    private val activeCalls = mutableMapOf<String, ActiveCall>()

    override fun onListenerConnected() {
        AndroidPlatformContext.applicationContext = applicationContext
        if (!CallDetectionStore.isEnabled()) return
        // Pick up a call that is already running if the system rebound us mid-call.
        val current = try {
            activeNotifications
        } catch (_: Exception) {
            null
        } ?: return
        current.filter { isCallNotification(it) }.forEach { track(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        AndroidPlatformContext.applicationContext = applicationContext
        if (!CallDetectionStore.isEnabled()) return
        if (!isCallNotification(sbn)) return
        track(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Remove tracked state regardless of the setting so nothing lingers in memory.
        val call = activeCalls.remove(sbn.key) ?: return
        AndroidPlatformContext.applicationContext = applicationContext
        if (!CallDetectionStore.isEnabled()) return
        val now = System.currentTimeMillis()
        val durationSeconds = (now - call.startedAt) / 1000L
        if (durationSeconds < CallDetectionStore.MIN_RECORDED_SECONDS) return
        val title = call.title
        val isNumber = title != null && NUMBER_REGEX.matches(title)
        CallDetectionStore.record(
            PhoneCallRecord(
                id = "${call.startedAt}-${sbn.key.hashCode()}",
                number = if (isNumber) title else null,
                contactName = if (!isNumber) title else null,
                startedAt = call.startedAt,
                durationSeconds = durationSeconds,
            )
        )
    }

    private fun isCallNotification(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        if (sbn.packageName == packageName) return false
        if (n.category != Notification.CATEGORY_CALL) return false
        return n.flags and Notification.FLAG_ONGOING_EVENT != 0
    }

    private fun track(sbn: StatusBarNotification) {
        val n = sbn.notification
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.ifEmpty { null }
        val now = System.currentTimeMillis()
        // CallStyle (API 31+): 2 = ongoing. Older dialers show a chronometer once connected.
        val connected = extras?.getInt(EXTRA_CALL_TYPE, 0) == CALL_TYPE_ONGOING ||
            extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) == true
        val existing = activeCalls[sbn.key]
        if (existing == null) {
            // A chronometer's base time is the moment the call connected.
            val start = if (connected && n.`when` in 1..now) n.`when` else now
            activeCalls[sbn.key] = ActiveCall(title, start, connected)
        } else if (connected && !existing.connected) {
            // Ringing is over: measure from the moment the call was picked up.
            existing.connected = true
            existing.startedAt = if (n.`when` in 1..now) n.`when` else now
        }
    }

    private companion object {
        const val EXTRA_CALL_TYPE = "android.callType"
        const val CALL_TYPE_ONGOING = 2
        val NUMBER_REGEX = Regex("^[+\\d\\s()\\-.]{5,}$")
    }
}

/** Calls detected by [CallNotificationListenerService], kept in app-private storage. */
internal object CallDetectionStore {
    const val MIN_RECORDED_SECONDS = 60L
    private const val ENABLED_KEY = "phone_integration_enabled"
    private const val CALLS_KEY = "phone_integration_detected_calls"
    private const val MAX_CALLS = 100
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PhoneCallRecord.serializer())

    fun isEnabled(): Boolean = PlatformStorage.getString(ENABLED_KEY) == "true"

    @Synchronized
    fun record(call: PhoneCallRecord) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        val updated = (load() + call).filter { it.startedAt > cutoff }.takeLast(MAX_CALLS)
        PlatformStorage.putString(CALLS_KEY, json.encodeToString(serializer, updated))
    }

    @Synchronized
    fun load(): List<PhoneCallRecord> {
        val raw = PlatformStorage.getString(CALLS_KEY) ?: return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clear() = PlatformStorage.putString(CALLS_KEY, null)
}
