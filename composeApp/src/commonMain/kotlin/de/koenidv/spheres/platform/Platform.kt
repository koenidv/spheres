package de.koenidv.spheres.platform

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

@Serializable
data class PickedContact(
    val name: String,
    val city: String? = null,
)

@Serializable
data class PlatformReminderItem(
    val id: String,
    val title: String,
    val body: String,
    val at: Long,
    val personId: String,
)

expect fun haptic(durationMs: Int = 8)
expect fun currentTimeMillis(): Long
expect fun randomUuid(): String

expect object PlatformStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

expect object PlatformNotifications {
    fun isSupported(): Boolean
    fun isGranted(): Boolean
    suspend fun requestPermission(): Boolean
    fun syncSchedule(items: List<PlatformReminderItem>, overdueCount: Int)
}

expect fun shareJsonFile(filename: String, jsonContent: String)
expect fun openExternalUrl(url: String)
expect suspend fun launchGoogleAuthFlow(authUrl: String, callbackScheme: String): String
expect suspend fun signInWithGoogleNative(serverClientId: String): String?

@Composable
expect fun rememberContactPickerLauncher(onPicked: (List<PickedContact>) -> Unit): () -> Unit

@Composable
expect fun StatusBarEffect(colorHex: Long, dimmed: Boolean)
