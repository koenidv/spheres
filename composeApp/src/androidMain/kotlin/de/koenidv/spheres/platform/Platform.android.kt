package de.koenidv.spheres.platform

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

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
        val launchIntent = Intent(context, de.koenidv.spheres.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        for (item in items) {
            if (item.at <= now && now - item.at < 24 * 60 * 60 * 1000L) {
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
                .setSmallIcon(R.drawable.ic_stat_spheres)
                .setColor(0xFF9D7CE7.toInt())
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .build()

            manager.notify(9999, summaryNotification)
        }
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

@Composable
actual fun rememberNativeDatePicker(
    initialDateMillis: Long,
    onDateSelected: (Long) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val tz = TimeZone.currentSystemDefault()
    return remember(context, initialDateMillis, onDateSelected) {
        {
            val initialDate = Instant.fromEpochMilliseconds(initialDateMillis).toLocalDateTime(tz).date
            DatePickerDialog(
                context,
                { _, year, monthZeroBased, dayOfMonth ->
                    val picked = LocalDateTime(year, monthZeroBased + 1, dayOfMonth, 12, 0, 0, 0)
                        .toInstant(tz)
                        .toEpochMilliseconds()
                    onDateSelected(picked)
                },
                initialDate.year,
                initialDate.monthNumber - 1,
                initialDate.dayOfMonth
            ).show()
        }
    }
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
