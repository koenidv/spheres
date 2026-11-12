package de.koenidv.spheres.platform

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

object AndroidPlatformContext {
    private var appContextRef: WeakReference<Context>? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    var pendingAuthContinuation: CancellableContinuation<String>? = null

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
        for (item in items) {
            if (item.at <= now && now - item.at < 24 * 60 * 60 * 1000L) {
                val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(context, CHANNEL_ID)
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(context)
                }
                    .setContentTitle(item.title)
                    .setContentText(item.body)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setAutoCancel(true)
                    .build()

                manager.notify(item.id.hashCode(), notification)
            }
        }

        if (overdueCount > 0) {
            val summaryNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
                .setContentTitle("Spheres Catch-ups")
                .setContentText("$overdueCount contacts need attention")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build()

            manager.notify(9999, summaryNotification)
        }
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

@Composable
actual fun rememberContactPickerLauncher(onPicked: (List<PickedContact>) -> Unit): () -> Unit {
    val context = LocalContext.current
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

private fun resolveContact(context: Context, contactUri: Uri): List<PickedContact> {
    var name: String? = null
    var contactId: String? = null
    val cr = context.contentResolver
    cr.query(contactUri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (idIdx >= 0) contactId = cursor.getString(idIdx)
            if (nameIdx >= 0) name = cursor.getString(nameIdx)
        }
    }

    if (name.isNullOrBlank()) return emptyList()

    var city: String? = null
    if (contactId != null) {
        try {
            val postalCursor = cr.query(
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.StructuredPostal.CITY),
                "${ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )
            postalCursor?.use { c ->
                if (c.moveToFirst()) {
                    val cityIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.CITY)
                    if (cityIdx >= 0) city = c.getString(cityIdx)
                }
            }
        } catch (_: SecurityException) {
        }
    }

    return listOf(PickedContact(name = name ?: "Contact", city = city))
}

@Composable
actual fun StatusBarEffect(colorHex: Long, dimmed: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: AndroidPlatformContext.currentActivity?.window
            window?.let { win ->
                val baseColor = Color(colorHex)
                val effectiveColor = if (dimmed) {
                    Color(
                        red = 0.757f * 0.7f + baseColor.red * 0.3f,
                        green = 0.757f * 0.7f + baseColor.green * 0.3f,
                        blue = 0.757f * 0.7f + baseColor.blue * 0.3f,
                        alpha = 1f
                    )
                } else {
                    baseColor
                }
                win.statusBarColor = effectiveColor.toArgb()
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                val luminance = 0.299f * effectiveColor.red + 0.587f * effectiveColor.green + 0.114f * effectiveColor.blue
                insetsController.isAppearanceLightStatusBars = luminance > 0.5f
            }
        }
    }
}
