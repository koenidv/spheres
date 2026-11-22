package de.koenidv.spheres.platform

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.provider.ContactsPickerSessionContract
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.koenidv.spheres.domain.Birthday
import de.koenidv.spheres.domain.parseContactBirthday
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelPanel
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import de.koenidv.spheres.R
import de.koenidv.spheres.domain.DEFAULT_UNLIMITED_PRICE
import de.koenidv.spheres.domain.UNLIMITED_PRODUCT_ID
import de.koenidv.spheres.domain.UNLIMITED_PURCHASE_OPTION_ID
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object AndroidPlatformContext {
    private var appContextRef: WeakReference<Context>? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    var pendingAuthContinuation: CancellableContinuation<String>? = null
    var pendingSettingsReturn: CompletableDeferred<Unit>? = null
    var pendingLocationPermission: CompletableDeferred<Boolean>? = null

    const val LOCATION_PERMISSION_REQUEST_CODE = 1003

    /** Call from the activity's onResume; releases a flow waiting for the user to return from system settings. */
    fun onActivityResumed() {
        pendingSettingsReturn?.complete(Unit)
        pendingSettingsReturn = null
    }

    fun onRequestPermissionsResult(requestCode: Int) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            pendingLocationPermission?.complete(PlatformLocation.isGranted())
            pendingLocationPermission = null
        }
    }

    var applicationContext: Context
        get() = appContextRef?.get() ?: error("AndroidPlatformContext not initialized")
        set(value) {
            appContextRef = WeakReference(value.applicationContext)
        }

    var currentActivity: Activity?
        get() = currentActivityRef?.get()
        set(value) {
            currentActivityRef = value?.let { WeakReference(it) }
        }

    fun init(activity: Activity) {
        applicationContext = activity.applicationContext
        currentActivity = activity
    }

    fun onAuthCallback(url: String) {
        val cont = pendingAuthContinuation ?: return
        pendingAuthContinuation = null
        if (cont.isActive) {
            cont.resume(url)
        }
    }
}

actual fun haptic(durationMs: Int) {
    try {
        val context = AndroidPlatformContext.applicationContext
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!vibrator.hasVibrator()) return

        val duration = durationMs.toLong().coerceAtLeast(1L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    } catch (_: Throwable) {
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun randomUuid(): String = UUID.randomUUID().toString()

actual fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

actual object PlatformStorage {
    private const val PREFS_NAME = "spheres_prefs"

    private val prefs: SharedPreferences
        get() = AndroidPlatformContext.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    actual fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) {
                remove(key)
            } else {
                putString(key, value)
            }
            apply()
        }
    }
}

actual object PlatformPhotoCache {
    private val cacheDir: File
        get() = File(AndroidPlatformContext.applicationContext.filesDir, "avatar_photos").apply {
            if (!exists()) mkdirs()
        }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hex.jpg")
    }

    actual fun get(key: String): ByteArray? = try {
        val file = fileFor(key)
        if (file.exists()) file.readBytes() else null
    } catch (_: Throwable) {
        null
    }

    actual fun put(key: String, bytes: ByteArray) {
        try {
            fileFor(key).writeBytes(bytes)
        } catch (_: Throwable) {
        }
    }

    actual fun delete(key: String) {
        try {
            fileFor(key).delete()
        } catch (_: Throwable) {
        }
    }
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Throwable) {
    null
}

actual object PlatformLocation {
    actual fun isSupported(): Boolean {
        val context = AndroidPlatformContext.applicationContext
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION) ||
            context.getSystemService(Context.LOCATION_SERVICE) != null
    }

    actual fun isGranted(): Boolean {
        val context = AndroidPlatformContext.applicationContext
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun requestPermission(): Boolean {
        if (isGranted()) return true
        val activity = AndroidPlatformContext.currentActivity ?: return false
        val deferred = CompletableDeferred<Boolean>()
        AndroidPlatformContext.pendingLocationPermission?.cancel()
        AndroidPlatformContext.pendingLocationPermission = deferred
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            AndroidPlatformContext.LOCATION_PERMISSION_REQUEST_CODE
        )
        return withTimeoutOrNull(60_000L) { deferred.await() } ?: isGranted()
    }

    @Suppress("MissingPermission")
    actual suspend fun getCurrentLocation(): UserLocation? {
        if (!isGranted()) return null
        val context = AndroidPlatformContext.applicationContext
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val lastKnown = bestLastKnownLocation(manager)
        val now = currentTimeMillis()
        if (lastKnown != null && now - lastKnown.time < 2 * 60_000L) {
            return lastKnown.toUserLocation()
        }

        val provider = pickLocationProvider(manager)
        val fresh = if (provider != null) {
            withTimeoutOrNull(6_000L) {
                requestSingleLocation(context, manager, provider)
            }
        } else null

        return (fresh ?: lastKnown)?.toUserLocation()
    }

    private fun pickLocationProvider(manager: LocationManager): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return candidates.firstOrNull { provider ->
            try {
                manager.isProviderEnabled(provider)
            } catch (_: Throwable) {
                false
            }
        }
    }

    @Suppress("MissingPermission")
    private fun bestLastKnownLocation(manager: LocationManager): Location? {
        val providers = try {
            manager.getProviders(true)
        } catch (_: Throwable) {
            emptyList()
        }
        var best: Location? = null
        for (provider in providers) {
            val loc = try {
                manager.getLastKnownLocation(provider)
            } catch (_: Throwable) {
                null
            } ?: continue
            if (best == null || loc.time > best.time) {
                best = loc
            }
        }
        return best
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun requestSingleLocation(
        context: Context,
        manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                manager.getCurrentLocation(
                    provider,
                    signal,
                    context.mainExecutor
                ) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    try {
                        manager.removeUpdates(this)
                    } catch (_: Throwable) {
                    }
                    if (cont.isActive) cont.resume(location)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            cont.invokeOnCancellation {
                try {
                    manager.removeUpdates(listener)
                } catch (_: Throwable) {
                }
            }
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun Location.toUserLocation(): UserLocation = UserLocation(
        lat = latitude,
        lng = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        updatedAt = if (time > 0L) time else currentTimeMillis()
    )
}

actual object PlatformNotifications {
    private const val CHANNEL_ID = "spheres_reminders"
    private const val CHANNEL_NAME = "Spheres Reminders"

    actual fun isSupported(): Boolean = true

    actual fun isGranted(): Boolean {
        val context = AndroidPlatformContext.applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        if (!manager.areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    actual suspend fun requestPermission(): Boolean {
        if (isGranted()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false

        val activity = AndroidPlatformContext.currentActivity
        if (activity != null) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            for (i in 0 until 10) {
                delay(500)
                if (isGranted()) return true
            }
        }
        return isGranted()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Spheres catch-up reminders"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    actual fun syncSchedule(items: List<PlatformReminderItem>, overdueCount: Int) {
        val context = AndroidPlatformContext.applicationContext
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (!isGranted()) return

        val now = currentTimeMillis()
        val launchIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply { setPackage(context.packageName) }).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val reminderPrefs = context.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE)
        val previous = loadScheduled(reminderPrefs)
        val posted = reminderPrefs.getStringSet(KEY_POSTED, emptySet()) ?: emptySet()

        // Drop alarms from the previous schedule; they are re-registered below if still relevant.
        previous.forEach { alarmManager?.cancel(reminderAlarmIntent(context, it)) }

        val upcoming = items.filter { it.at > now && it.at - now < REMINDER_HORIZON_MS }
        upcoming.forEach { scheduleAlarm(context, alarmManager, it) }
        saveScheduled(reminderPrefs, upcoming)

        // Items that came due while the app was closed were posted by their alarm. Anything due
        // and not yet posted (e.g. just created or snoozed into the past) is shown right away.
        val due = items.filter { it.at <= now && now - it.at < 24 * 60 * 60 * 1000L }
        due.filter { it.id !in posted }.forEach { postReminder(context, it) }
        reminderPrefs.edit().putStringSet(KEY_POSTED, (posted + due.map { it.id }).intersect(items.map { it.id }.toSet())).apply()

        if (overdueCount > 0) {
            val summaryNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
                .setContentTitle("Spheres Catch-ups")
                .setContentText("$overdueCount contacts need attention")
                .setSmallIcon(R.drawable.ic_stat_spheres)
                .setColor(0xFF9D7CE7.toInt())
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .build()

            manager.notify(9999, summaryNotification)
        }
    }

    private const val REMINDER_PREFS = "spheres_reminder_alarms"
    private const val KEY_SCHEDULED = "scheduled"
    private const val KEY_POSTED = "posted"
    private const val REMINDER_HORIZON_MS = 30L * 24 * 60 * 60 * 1000

    private fun reminderAlarmIntent(context: Context, item: PlatformReminderItem): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_ID, item.id)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, item.title)
            putExtra(ReminderAlarmReceiver.EXTRA_BODY, item.body)
            putExtra(ReminderAlarmReceiver.EXTRA_PERSON_ID, item.personId)
            putExtra(ReminderAlarmReceiver.EXTRA_AT, item.at)
        }
        return PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleAlarm(context: Context, alarmManager: AlarmManager?, item: PlatformReminderItem) {
        // Inexact (but doze-safe) alarm: needs no special permission and may drift a few minutes.
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, reminderAlarmIntent(context, item))
    }

    private fun saveScheduled(prefs: SharedPreferences, items: List<PlatformReminderItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id).put("title", it.title).put("body", it.body)
                    .put("at", it.at).put("personId", it.personId)
            )
        }
        prefs.edit().putString(KEY_SCHEDULED, array.toString()).apply()
    }

    private fun loadScheduled(prefs: SharedPreferences): List<PlatformReminderItem> {
        val raw = prefs.getString(KEY_SCHEDULED, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                PlatformReminderItem(o.getString("id"), o.getString("title"), o.getString("body"), o.getLong("at"), o.getString("personId"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Re-registers all alarms after a reboot, which clears them. */
    internal fun restoreAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val now = currentTimeMillis()
        loadScheduled(context.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE))
            .filter { it.at > now - 24 * 60 * 60 * 1000L }
            .forEach { scheduleAlarm(context, alarmManager, it) }
    }

    internal fun markPosted(context: Context, id: String) {
        val prefs = context.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_POSTED, (prefs.getStringSet(KEY_POSTED, emptySet()) ?: emptySet()) + id).apply()
    }

    internal fun postReminder(context: Context, item: PlatformReminderItem) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (!isGranted()) return
        val launchIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply { setPackage(context.packageName) }).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationId = item.id.hashCode()
        val checkInAction = buildNotificationAction(
            context = context,
            notificationId = notificationId,
            personId = item.personId,
            action = NotificationActionType.CHECKIN,
            title = "Check in",
            requestCode = (item.id + ":checkin").hashCode()
        )
        val snoozeAction = buildNotificationAction(
            context = context,
            notificationId = notificationId,
            personId = item.personId,
            action = NotificationActionType.SNOOZE,
            title = "Snooze 3d",
            requestCode = (item.id + ":snooze").hashCode()
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
            .setContentTitle(item.title)
            .setContentText(item.body)
            .setSmallIcon(R.drawable.ic_stat_spheres)
            .setColor(0xFF9D7CE7.toInt())
            .setContentIntent(contentPendingIntent)
            .addAction(checkInAction)
            .addAction(snoozeAction)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
    }

    private fun buildNotificationAction(
        context: Context,
        notificationId: Int,
        personId: String,
        action: NotificationActionType,
        title: String,
        requestCode: Int,
    ): Notification.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            putExtra(NotificationActionReceiver.EXTRA_PERSON_ID, personId)
            putExtra(NotificationActionReceiver.EXTRA_ACTION, action.name)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        return Notification.Action.Builder(0, title, pendingIntent).build()
    }
}

/** Fires at a reminder's scheduled time, even when the app process is not running. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AndroidPlatformContext.applicationContext = context.applicationContext
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val item = PlatformReminderItem(
            id = id,
            title = intent.getStringExtra(EXTRA_TITLE) ?: return,
            body = intent.getStringExtra(EXTRA_BODY) ?: "",
            at = intent.getLongExtra(EXTRA_AT, 0L),
            personId = intent.getStringExtra(EXTRA_PERSON_ID) ?: return,
        )
        PlatformNotifications.postReminder(context, item)
        PlatformNotifications.markPosted(context, id)
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_PERSON_ID = "person_id"
        const val EXTRA_AT = "at"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AndroidPlatformContext.applicationContext = context.applicationContext
        PlatformNotifications.restoreAlarms(context)
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AndroidPlatformContext.applicationContext = context.applicationContext
        val personId = intent.getStringExtra(EXTRA_PERSON_ID) ?: return
        val actionName = intent.getStringExtra(EXTRA_ACTION) ?: return
        val action = NotificationActionType.entries.firstOrNull { it.name == actionName } ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (notificationId != 0) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(notificationId)
        }
        NotificationActionBridge.dispatch(personId, action)
    }

    companion object {
        const val EXTRA_PERSON_ID = "person_id"
        const val EXTRA_ACTION = "action"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}

actual object PlatformPhoneIntegration {
    actual fun isSupported(): Boolean = true

    /** True while the user has granted Spheres notification access in system settings. */
    actual fun isGranted(): Boolean {
        val context = AndroidPlatformContext.applicationContext
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    /**
     * Must only be called after the user accepted the in-app disclosure. Asks for the optional
     * contacts permission, then opens the system notification access screen and waits for the
     * user to come back.
     */
    actual suspend fun requestPermission(): Boolean {
        val activity = AndroidPlatformContext.currentActivity ?: return false
        if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_CONTACTS), 1002)
            // Contacts are optional (they only improve matching), so just give the dialog a moment.
            for (i in 0 until 20) {
                delay(500)
                if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) break
            }
        }
        if (isGranted()) return true
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(activity, CallNotificationListenerService::class.java).flattenToString()
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        val returned = CompletableDeferred<Unit>()
        AndroidPlatformContext.pendingSettingsReturn = returned
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            AndroidPlatformContext.pendingSettingsReturn = null
            return false
        }
        returned.await()
        return isGranted()
    }

    actual fun fetchCallsLongerThan(minDurationSeconds: Long, sinceMillis: Long): List<PhoneCallRecord> {
        if (!isGranted()) return emptyList()
        val context = AndroidPlatformContext.applicationContext
        val canReadContacts =
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        return CallDetectionStore.load()
            .filter { it.durationSeconds > minDurationSeconds && it.startedAt > sinceMillis }
            .sortedBy { it.startedAt }
            .map { call ->
                if (!canReadContacts) return@map call
                val lookup = when {
                    !call.number.isNullOrBlank() -> lookupContactByNumber(context, call.number)
                    !call.contactName.isNullOrBlank() -> lookupContactByName(context, call.contactName)
                    else -> null
                } ?: return@map call
                call.copy(contactId = lookup.first, contactName = call.contactName ?: lookup.second)
            }
    }

    private fun lookupContactByName(context: Context, name: String): Pair<String?, String?>? {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name))
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                // Only trust an unambiguous exact match.
                if (cursor.count == 1 && cursor.moveToFirst() &&
                    cursor.getString(1)?.equals(name, ignoreCase = true) == true
                ) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() } to cursor.getString(1)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun lookupContactByNumber(context: Context, phoneNumber: String): Pair<String?, String?>? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ContactsContract.PhoneLookup.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val key = cursor.getString(0)?.takeIf { it.isNotBlank() }
                    val name = cursor.getString(1)?.takeIf { it.isNotBlank() }
                    key to name
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    actual fun getPhoneNumbersForContact(contactId: String): List<String> {
        val context = AndroidPlatformContext.applicationContext
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val numbers = mutableListOf<String>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.Contacts.LOOKUP_KEY} = ? OR ${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId, contactId),
                null
            )?.use { c ->
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val num = if (numIdx >= 0) c.getString(numIdx)?.trim()?.ifEmpty { null } else null
                    if (num != null) numbers.add(num)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        return numbers.distinct()
    }
}

actual fun shareJsonFile(filename: String, jsonContent: String) {
    try {
        val context = AndroidPlatformContext.applicationContext
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, filename)
            putExtra(Intent.EXTRA_TEXT, jsonContent)
        }
        val chooser = Intent.createChooser(intent, "Share $filename").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Throwable) {
    }
}

actual fun openExternalUrl(url: String) {
    try {
        val context = AndroidPlatformContext.applicationContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
    }
}

actual suspend fun launchGoogleAuthFlow(authUrl: String, callbackScheme: String): String =
    suspendCancellableCoroutine { cont ->
        AndroidPlatformContext.pendingAuthContinuation?.cancel()
        AndroidPlatformContext.pendingAuthContinuation = cont
        cont.invokeOnCancellation {
            if (AndroidPlatformContext.pendingAuthContinuation === cont) {
                AndroidPlatformContext.pendingAuthContinuation = null
            }
        }
        try {
            val context = AndroidPlatformContext.currentActivity ?: AndroidPlatformContext.applicationContext
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            AndroidPlatformContext.pendingAuthContinuation = null
            if (cont.isActive) {
                cont.resumeWithException(Exception("Could not open browser for sign-in: ${e.message}"))
            }
        }
    }

actual suspend fun signInWithGoogleNative(serverClientId: String): String? {
    val activity = AndroidPlatformContext.currentActivity
        ?: throw Exception("Activity not available for Google Sign-In")
    val credentialManager = CredentialManager.create(activity)
    return try {
        val result = try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            credentialManager.getCredential(activity, request)
        } catch (e: NoCredentialException) {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            credentialManager.getCredential(activity, request)
        }
        GoogleIdTokenCredential.createFrom(result.credential.data).idToken
    } catch (e: GetCredentialCancellationException) {
        throw Exception("Sign-in cancelled")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw Exception(e.message ?: "Google Sign-In failed")
    }
}

actual fun isAppleSignInSupported(): Boolean = false

actual suspend fun signInWithAppleNative(): AppleSignInCredential =
    throw UnsupportedOperationException("Sign in with Apple is not available on Android")

actual fun appleLogoBitmap(): ImageBitmap? = null

@Composable
actual fun rememberContactPickerLauncher(
    allowMultiple: Boolean,
    onPicked: (List<PickedContact>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (Build.VERSION.SDK_INT >= 37) {
        val sessionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val sessionUri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
            if (sessionUri != null) {
                scope.launch {
                    val contacts = withContext(Dispatchers.IO) {
                        resolveSessionContacts(context, sessionUri)
                    }
                    if (contacts.isNotEmpty()) {
                        onPicked(contacts)
                    }
                }
            }
        }
        return remember(sessionLauncher, allowMultiple) {
            {
                val intent = Intent(ContactsPickerSessionContract.ACTION_PICK_CONTACTS).apply {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
                    putStringArrayListExtra(
                        ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
                        arrayListOf(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                        )
                    )
                }
                sessionLauncher.launch(intent)
            }
        }
    }

    if (!allowMultiple) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickContact()
        ) { uri ->
            if (uri != null) {
                val contacts = resolveContact(context, uri)
                if (contacts.isNotEmpty()) {
                    onPicked(contacts)
                }
            }
        }
        return remember(launcher) {
            { launcher.launch(null) }
        }
    }

    var showDialog by remember { mutableStateOf(false) }
    var allContacts by remember { mutableStateOf<List<ContactRow>>(emptyList()) }

    val openDialog = {
        scope.launch {
            allContacts = withContext(Dispatchers.IO) { loadContactRows(context) }
            showDialog = true
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openDialog()
        }
    }

    if (showDialog) {
        MultiContactPickerDialog(
            contacts = allContacts,
            onDismiss = { showDialog = false },
            onConfirm = { selected ->
                showDialog = false
                if (selected.isNotEmpty()) {
                    scope.launch {
                        val resolved = withContext(Dispatchers.IO) {
                            selected.map { row ->
                                readContactDetails(context, row.rawId, row.lookupKey, row.displayName)
                            }
                        }
                        if (resolved.isNotEmpty()) {
                            onPicked(resolved)
                        }
                    }
                }
            }
        )
    }

    return remember(permLauncher) {
        {
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                openDialog()
            } else {
                permLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }
}

@Composable
actual fun rememberPhotoPickerLauncher(onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { encodeSquareAvatarJpeg(context, uri) }
                if (bytes != null) {
                    onPicked(bytes)
                }
            }
        }
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

actual fun cropSquareAvatarJpeg(
    sourceBytes: ByteArray,
    leftFrac: Float,
    topFrac: Float,
    widthFrac: Float,
    heightFrac: Float,
): ByteArray? {
    return try {
        val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return null
        val w = decoded.width.coerceAtLeast(1)
        val h = decoded.height.coerceAtLeast(1)
        val rawW = (widthFrac * w).roundToInt().coerceIn(1, w)
        val rawH = (heightFrac * h).roundToInt().coerceIn(1, h)
        val side = minOf(rawW, rawH)
        val srcX = (leftFrac * w).roundToInt().coerceIn(0, (w - side).coerceAtLeast(0))
        val srcY = (topFrac * h).roundToInt().coerceIn(0, (h - side).coerceAtLeast(0))
        val cropped = Bitmap.createBitmap(decoded, srcX, srcY, side, side)
        val target = minOf(side, 512)
        val finalBitmap = if (side != target) Bitmap.createScaledBitmap(cropped, target, target, true) else cropped
        ByteArrayOutputStream().use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            if (finalBitmap !== cropped && finalBitmap !== decoded) finalBitmap.recycle()
            if (cropped !== decoded) cropped.recycle()
            decoded.recycle()
            out.toByteArray()
        }
    } catch (_: Throwable) {
        null
    }
}

private fun encodeSquareAvatarJpeg(context: Context, uri: Uri): ByteArray? {
    return try {
        val cr = context.contentResolver
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(cr, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width.coerceAtLeast(1)
                val h = info.size.height.coerceAtLeast(1)
                val maxSide = maxOf(w, h)
                if (maxSide > 1600) {
                    val scale = 1600f / maxSide
                    decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (maxSide / (sample * 2) >= 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } ?: return null

        ByteArrayOutputStream().use { out ->
            decoded.compress(Bitmap.CompressFormat.JPEG, 92, out)
            decoded.recycle()
            out.toByteArray()
        }
    } catch (_: Throwable) {
        null
    }
}

private data class ContactRow(
    val rawId: String,
    val lookupKey: String?,
    val displayName: String,
)

private fun loadContactRows(context: Context): List<ContactRow> {
    val rows = mutableListOf<ContactRow>()
    try {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL",
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val keyIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val rawId = if (idIdx >= 0) cursor.getString(idIdx) else null
                val lookupKey = if (keyIdx >= 0) cursor.getString(keyIdx)?.takeIf { it.isNotBlank() } else null
                val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } else null
                if (!rawId.isNullOrBlank() && !name.isNullOrBlank()) {
                    rows.add(ContactRow(rawId = rawId, lookupKey = lookupKey, displayName = name))
                }
            }
        }
    } catch (_: SecurityException) {
    } catch (_: Exception) {
    }
    return rows
}

@Composable
private fun MultiContactPickerDialog(
    contacts: List<ContactRow>,
    onDismiss: () -> Unit,
    onConfirm: (List<ContactRow>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filtered = remember(contacts, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) contacts else contacts.filter { it.displayName.lowercase().contains(q) }
    }

    Dialog(onDismissRequest = onDismiss) {
        PixelPanel(
            color = Color.White,
            raised = true,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Import contacts",
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontSize = 18.sp,
                        color = Ink
                    )
                )
                PixelTextInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search contacts"
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = if (contacts.isEmpty()) "No contacts found" else "No matching contacts",
                        style = TextStyle(
                            fontFamily = SansFontFamily(),
                            fontSize = 14.sp,
                            color = Muted
                        ),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.rawId }) { row ->
                            val checked = row.rawId in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(PxClipShape)
                                    .clickable {
                                        selectedIds = if (checked) selectedIds - row.rawId else selectedIds + row.rawId
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(PxClipShape)
                                        .background(if (checked) Ink else Ink.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (checked) {
                                        PixelIcon(name = IconName.CHECK, size = 14.dp, tint = Color.White)
                                    }
                                }
                                Text(
                                    text = row.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 15.sp,
                                        color = Ink
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButton(
                        text = "Cancel",
                        variant = ButtonVariant.SECONDARY,
                        size = ButtonSize.MD,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    PixelButton(
                        text = if (selectedIds.isEmpty()) "Import" else "Import (${selectedIds.size})",
                        variant = ButtonVariant.PRIMARY,
                        size = ButtonSize.MD,
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onConfirm(contacts.filter { it.rawId in selectedIds })
                        }
                    )
                }
            }
        }
    }
}

private fun resolveSessionContacts(context: Context, sessionUri: Uri): List<PickedContact> {
    val cr = context.contentResolver
    val projection = arrayOf(
        ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ContactsContract.Data.MIMETYPE,
        ContactsContract.Data.DATA1,
        ContactsContract.Data.DATA2,
        ContactsContract.Data.DATA4,
        ContactsContract.Data.DATA7,
        ContactsContract.Data.DATA10,
    )

    class Acc(
        val lookupKey: String?,
        var name: String,
    ) {
        val phones = mutableListOf<String>()
        var city: String? = null
        var address: String? = null
        var country: String? = null
        var birthday: Birthday? = null
    }

    val byKey = linkedMapOf<String, Acc>()
    try {
        cr.query(sessionUri, projection, null, null, null)?.use { c ->
            val keyIdx = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val mimeIdx = c.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val d1Idx = c.getColumnIndex(ContactsContract.Data.DATA1)
            val d2Idx = c.getColumnIndex(ContactsContract.Data.DATA2)
            val d4Idx = c.getColumnIndex(ContactsContract.Data.DATA4)
            val d7Idx = c.getColumnIndex(ContactsContract.Data.DATA7)
            val d10Idx = c.getColumnIndex(ContactsContract.Data.DATA10)

            while (c.moveToNext()) {
                val lookupKey = if (keyIdx >= 0) c.getString(keyIdx)?.takeIf { it.isNotBlank() } else null
                val name = (if (nameIdx >= 0) c.getString(nameIdx) else null)?.trim()?.ifEmpty { null } ?: continue
                val mapKey = lookupKey ?: name
                val acc = byKey.getOrPut(mapKey) { Acc(lookupKey, name) }

                val mime = if (mimeIdx >= 0) c.getString(mimeIdx) else null
                when (mime) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val num = if (d1Idx >= 0) c.getString(d1Idx)?.trim()?.ifEmpty { null } else null
                        if (num != null) acc.phones.add(num)
                    }
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        if (acc.address == null && d4Idx >= 0) {
                            acc.address = c.getString(d4Idx)?.trim()?.ifEmpty { null }
                        }
                        if (acc.city == null && d7Idx >= 0) {
                            acc.city = c.getString(d7Idx)?.trim()?.ifEmpty { null }
                        }
                        if (acc.country == null && d10Idx >= 0) {
                            acc.country = c.getString(d10Idx)?.trim()?.ifEmpty { null }
                        }
                    }
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                        if (acc.birthday == null && d1Idx >= 0) {
                            val eventType = if (d2Idx >= 0) c.getInt(d2Idx) else ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
                            if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) {
                                acc.birthday = parseContactBirthday(c.getString(d1Idx))
                            }
                        }
                    }
                }
            }
        }
    } catch (_: Throwable) {
    }

    return byKey.values.map { acc ->
        PickedContact(
            name = acc.name,
            contactId = acc.lookupKey ?: acc.name,
            phoneNumbers = acc.phones.distinct(),
            city = acc.city,
            address = acc.address,
            country = acc.country,
            birthday = acc.birthday,
        )
    }
}

private fun resolveContact(context: Context, contactUri: Uri): List<PickedContact> {
    var name: String? = null
    var rawId: String? = null
    var lookupKey: String? = null
    val cr = context.contentResolver
    cr.query(
        contactUri,
        arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME
        ),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val keyIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (idIdx >= 0) rawId = cursor.getString(idIdx)
            if (keyIdx >= 0) lookupKey = cursor.getString(keyIdx)?.takeIf { it.isNotBlank() }
            if (nameIdx >= 0) name = cursor.getString(nameIdx)
        }
    }

    val safeName = name?.takeIf { it.isNotBlank() } ?: return emptyList()
    val safeId = rawId ?: return listOf(PickedContact(name = safeName, contactId = lookupKey))
    return listOf(readContactDetails(context, safeId, lookupKey, safeName))
}

private fun readContactDetails(
    context: Context,
    rawId: String,
    lookupKey: String?,
    displayName: String,
): PickedContact {
    val cr = context.contentResolver
    var city: String? = null
    var address: String? = null
    var country: String? = null
    var birthday: Birthday? = null
    val phoneNumbers = mutableListOf<String>()

    try {
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(rawId),
            null
        )?.use { c ->
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val num = if (numIdx >= 0) c.getString(numIdx)?.trim()?.ifEmpty { null } else null
                if (num != null) phoneNumbers.add(num)
            }
        }
    } catch (_: SecurityException) {
    }

    try {
        cr.query(
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.StructuredPostal.STREET,
                ContactsContract.CommonDataKinds.StructuredPostal.CITY,
                ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,
            ),
            "${ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID} = ?",
            arrayOf(rawId),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                val streetIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.STREET)
                val cityIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.CITY)
                val countryIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY)
                if (streetIdx >= 0) address = c.getString(streetIdx)?.trim()?.ifEmpty { null }
                if (cityIdx >= 0) city = c.getString(cityIdx)?.trim()?.ifEmpty { null }
                if (countryIdx >= 0) country = c.getString(countryIdx)?.trim()?.ifEmpty { null }
            }
        }
    } catch (_: SecurityException) {
    }

    try {
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Event.START_DATE),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
            arrayOf(
                rawId,
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
            ),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                val dateIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)
                if (dateIdx >= 0) {
                    birthday = parseContactBirthday(c.getString(dateIdx))
                }
            }
        }
    } catch (_: SecurityException) {
    }

    return PickedContact(
        name = displayName,
        contactId = lookupKey ?: rawId,
        phoneNumbers = phoneNumbers.distinct(),
        city = city,
        address = address,
        country = country,
        birthday = birthday,
    )
}

@Composable
actual fun StatusBarEffect(colorHex: Long, dimmed: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: AndroidPlatformContext.currentActivity?.window
            window?.let { win ->
                win.statusBarColor = android.graphics.Color.TRANSPARENT
                win.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(win, win.decorView).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

actual object PlatformBilling {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var billingClient: BillingClient? = null
    private var entitlementCallback: ((Boolean, String?, String?) -> Unit)? = null
    private var cachedProductDetails: ProductDetails? = null
    private var cachedFormattedPrice: String? = null
    private var pendingPurchaseContinuation: CancellableContinuation<PurchaseOutcome>? = null
    private var pendingPurchaseProductId: String = UNLIMITED_PRODUCT_ID

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        scope.launch {
            handlePurchaseResult(billingResult, purchases)
        }
    }

    actual fun initialize(onEntitlementVerified: (verified: Boolean, platform: String?, purchaseToken: String?) -> Unit) {
        entitlementCallback = onEntitlementVerified
        try {
            val client = ensureClient() ?: return
            scope.launch {
                if (ensureConnected(client)) {
                    verifyOwnedPurchases(client, UNLIMITED_PRODUCT_ID)
                }
            }
        } catch (_: Throwable) {
        }
    }

    actual suspend fun queryProduct(productId: String): BillingProductInfo {
        val client = ensureClient()
            ?: return BillingProductInfo(productId, DEFAULT_UNLIMITED_PRICE, isStoreConnected = false)
        if (!ensureConnected(client)) {
            return BillingProductInfo(productId, cachedFormattedPrice ?: DEFAULT_UNLIMITED_PRICE, isStoreConnected = false)
        }
        val details = fetchProductDetails(client, productId)
        val price = details?.let { selectOffer(it)?.formattedPrice }
        if (!price.isNullOrBlank()) {
            cachedFormattedPrice = price
        }
        return BillingProductInfo(
            productId = productId,
            formattedPrice = price ?: cachedFormattedPrice ?: DEFAULT_UNLIMITED_PRICE,
            isStoreConnected = details != null
        )
    }

    actual suspend fun purchaseUnlimited(productId: String): PurchaseOutcome {
        pendingPurchaseProductId = productId
        val activity = AndroidPlatformContext.currentActivity
            ?: return PurchaseOutcome.Error("Purchase UI could not be opened right now.")

        val client = ensureClient()
        val connected = client != null && ensureConnected(client)
        val details = if (connected) {
            cachedProductDetails?.takeIf { it.productId == productId }
                ?: fetchProductDetails(client, productId)
        } else {
            null
        }

        if (connected && details != null) {
            val existingToken = findOwnedPurchaseToken(client, productId)
            if (existingToken != null) {
                return PurchaseOutcome.Success(productId, platform = "android", purchaseToken = existingToken)
            }
            return suspendCancellableCoroutine { cont ->
                pendingPurchaseContinuation?.cancel()
                pendingPurchaseContinuation = cont
                cont.invokeOnCancellation {
                    if (pendingPurchaseContinuation === cont) {
                        pendingPurchaseContinuation = null
                    }
                }
                scope.launch(Dispatchers.Main) {
                    val offerToken = selectOffer(details)?.offerToken
                    val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                    if (!offerToken.isNullOrBlank()) {
                        productParamsBuilder.setOfferToken(offerToken)
                    }
                    val flowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
                        .build()
                    val launchResult = client.launchBillingFlow(activity, flowParams)
                    if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        val c = pendingPurchaseContinuation
                        pendingPurchaseContinuation = null
                        if (c != null && c.isActive) {
                            if (launchResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                                c.resume(PurchaseOutcome.Success(productId, platform = "android"))
                            } else if (launchResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                                c.resume(PurchaseOutcome.Cancelled)
                            } else {
                                c.resume(PurchaseOutcome.Error(launchResult.debugMessage.ifBlank { "Google Play unavailable" }))
                            }
                        }
                    }
                }
            }
        }

        val priceLabel = cachedFormattedPrice ?: DEFAULT_UNLIMITED_PRICE
        return showFallbackPurchaseDialog(activity, productId, priceLabel)
    }

    actual suspend fun restorePurchases(productId: String): RestoreOutcome {
        val client = ensureClient() ?: return RestoreOutcome.NotFound
        if (!ensureConnected(client)) return RestoreOutcome.NotFound
        val token = findOwnedPurchaseToken(client, productId) ?: return RestoreOutcome.NotFound
        return RestoreOutcome.Found(platform = "android", purchaseToken = token)
    }

    private fun ensureClient(): BillingClient? {
        billingClient?.let { return it }
        val context = try {
            AndroidPlatformContext.applicationContext
        } catch (_: Throwable) {
            return null
        }
        val params = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()
        return BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(params)
            .build()
            .also { billingClient = it }
    }

    private suspend fun ensureConnected(client: BillingClient): Boolean {
        if (client.isReady) return true
        return withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { cont ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) {
                            cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) {
                            cont.resume(false)
                        }
                    }
                })
            }
        } ?: false
    }

    private fun selectOffer(details: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? {
        val offers = details.oneTimePurchaseOfferDetailsList.orEmpty()
        return offers.firstOrNull { it.purchaseOptionId == UNLIMITED_PURCHASE_OPTION_ID }
            ?: offers.firstOrNull()
            ?: details.oneTimePurchaseOfferDetails
    }

    private suspend fun fetchProductDetails(client: BillingClient, productId: String): ProductDetails? {
        return try {
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build()
            val result = withContext(Dispatchers.IO) {
                client.queryProductDetails(params)
            }
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.productDetailsList?.firstOrNull { it.productId == productId }?.also {
                    cachedProductDetails = it
                }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun findOwnedPurchaseToken(client: BillingClient, productId: String): String? {
        return try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val result = withContext(Dispatchers.IO) {
                client.queryPurchasesAsync(params)
            }
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                return null
            }
            val matching = result.purchasesList.firstOrNull { purchase ->
                purchase.products.contains(productId) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            } ?: return null
            acknowledgeIfNeeded(client, matching)
            matching.purchaseToken
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun verifyOwnedPurchases(client: BillingClient, productId: String): Boolean {
        return try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val result = withContext(Dispatchers.IO) {
                client.queryPurchasesAsync(params)
            }
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                return false
            }
            val matching = result.purchasesList.firstOrNull { purchase ->
                purchase.products.contains(productId) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (matching != null) {
                acknowledgeIfNeeded(client, matching)
                entitlementCallback?.invoke(true, "android", matching.purchaseToken)
                true
            } else {
                entitlementCallback?.invoke(false, "android", null)
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun acknowledgeIfNeeded(client: BillingClient, purchase: Purchase) {
        if (purchase.isAcknowledged) return
        try {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            withContext(Dispatchers.IO) {
                client.acknowledgePurchase(ackParams)
            }
        } catch (_: Throwable) {
        }
    }

    private suspend fun handlePurchaseResult(result: BillingResult, purchases: List<Purchase>?) {
        val cont = pendingPurchaseContinuation
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchased = purchases?.firstOrNull {
                    it.products.contains(pendingPurchaseProductId) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (purchased != null) {
                    billingClient?.let { acknowledgeIfNeeded(it, purchased) }
                    entitlementCallback?.invoke(true, "android", purchased.purchaseToken)
                    pendingPurchaseContinuation = null
                    if (cont != null && cont.isActive) {
                        cont.resume(
                            PurchaseOutcome.Success(
                                productId = pendingPurchaseProductId,
                                platform = "android",
                                purchaseToken = purchased.purchaseToken
                            )
                        )
                    }
                } else {
                    val isPending = purchases?.any {
                        it.products.contains(pendingPurchaseProductId) &&
                            it.purchaseState == Purchase.PurchaseState.PENDING
                    } == true
                    pendingPurchaseContinuation = null
                    if (cont != null && cont.isActive) {
                        if (isPending) {
                            cont.resume(PurchaseOutcome.Error("Purchase is pending approval in Google Play."))
                        } else {
                            cont.resume(PurchaseOutcome.Cancelled)
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                pendingPurchaseContinuation = null
                if (cont != null && cont.isActive) {
                    cont.resume(PurchaseOutcome.Cancelled)
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                entitlementCallback?.invoke(true, "android", null)
                pendingPurchaseContinuation = null
                if (cont != null && cont.isActive) {
                    cont.resume(PurchaseOutcome.Success(pendingPurchaseProductId, platform = "android"))
                }
            }
            else -> {
                pendingPurchaseContinuation = null
                if (cont != null && cont.isActive) {
                    cont.resume(
                        PurchaseOutcome.Error(
                            result.debugMessage.ifBlank { "Purchase could not be completed." }
                        )
                    )
                }
            }
        }
    }

    private suspend fun showFallbackPurchaseDialog(
        activity: Activity,
        productId: String,
        priceLabel: String,
    ): PurchaseOutcome = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Spheres Unlimited")
                .setMessage(
                    "One-time purchase · $priceLabel\n\nUnlock unlimited connections and spheres forever across all your devices."
                )
                .setPositiveButton("Buy · $priceLabel") { _, _ ->
                    if (cont.isActive) {
                        entitlementCallback?.invoke(true, "debug_fallback", null)
                        cont.resume(PurchaseOutcome.Success(productId, platform = "debug_fallback"))
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    if (cont.isActive) {
                        cont.resume(PurchaseOutcome.Cancelled)
                    }
                }
                .setOnCancelListener {
                    if (cont.isActive) {
                        cont.resume(PurchaseOutcome.Cancelled)
                    }
                }
                .create()
            cont.invokeOnCancellation {
                dialog.dismiss()
            }
            dialog.show()
        }
    }
}
