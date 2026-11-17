package de.koenidv.spheres.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import de.koenidv.spheres.domain.DEFAULT_UNLIMITED_PRICE
import de.koenidv.spheres.domain.UNLIMITED_PRODUCT_ID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.skia.Image
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Contacts.CNContact
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPostalAddress
import platform.ContactsUI.CNContactPickerDelegateProtocol
import platform.ContactsUI.CNContactPickerViewController
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSPersonNameComponentsFormatter
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.StoreKit.SKErrorCode
import platform.StoreKit.SKPayment
import platform.StoreKit.SKPaymentQueue
import platform.StoreKit.SKPaymentTransaction
import platform.StoreKit.SKPaymentTransactionObserverProtocol
import platform.StoreKit.SKPaymentTransactionState
import platform.StoreKit.SKProduct
import platform.StoreKit.SKProductsRequest
import platform.StoreKit.SKProductsRequestDelegateProtocol
import platform.StoreKit.SKProductsResponse
import platform.StoreKit.SKRequest
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAction
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.UIKit.setStatusBarStyle
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

actual fun haptic(durationMs: Int) {
    val style = when {
        durationMs > 20 -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
        durationMs > 10 -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
        else -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
    }
    val generator = UIImpactFeedbackGenerator(style)
    generator.prepare()
    generator.impactOccurred()
}

actual fun currentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}

actual fun randomUuid(): String {
    return NSUUID().UUIDString
}

actual object PlatformStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? {
        return defaults.stringForKey(key)
    }

    actual fun putString(key: String, value: String?) {
        if (value == null) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(value, forKey = key)
        }
    }
}

actual object PlatformPhotoCache {
    @OptIn(ExperimentalForeignApi::class)
    private fun cacheDirPath(): String {
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val base = dirs.firstOrNull() as? String ?: NSTemporaryDirectory()
        val path = "$base/avatar_photos"
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) {
            fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        }
        return path
    }

    private fun fnvHex(key: String): String {
        var h = -3750763034362895579L
        for (b in key.encodeToByteArray()) {
            h = h xor (b.toLong() and 0xffL)
            h *= 1099511628211L
        }
        return h.toULong().toString(16) + "_" + key.length
    }

    private fun filePath(key: String): String = "${cacheDirPath()}/${fnvHex(key)}.jpg"

    @OptIn(ExperimentalForeignApi::class)
    actual fun get(key: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(filePath(key)) ?: return null
        return data.toByteArray()
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun put(key: String, bytes: ByteArray) {
        val data = bytes.toNSData() ?: return
        data.writeToFile(filePath(key), atomically = true)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun delete(key: String) {
        NSFileManager.defaultManager.removeItemAtPath(filePath(key), error = null)
    }
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Throwable) {
    null
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len <= 0) return ByteArray(0)
    return ByteArray(len).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData? {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

actual object PlatformNotifications {
    private const val CATEGORY_ID = "SPHERES_REMINDER"
    private const val ACTION_CHECKIN = "CHECKIN_ACTION"
    private const val ACTION_SNOOZE = "SNOOZE_ACTION"

    private var cachedGranted: Boolean = false
    private val delegate = object : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit
        ) {
            withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
        }

        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit
        ) {
            val userInfo = didReceiveNotificationResponse.notification.request.content.userInfo
            val personId = userInfo["personId"] as? String
            val action = when (didReceiveNotificationResponse.actionIdentifier) {
                ACTION_CHECKIN -> NotificationActionType.CHECKIN
                ACTION_SNOOZE -> NotificationActionType.SNOOZE
                else -> null
            }
            if (personId != null && action != null) {
                dispatch_async(dispatch_get_main_queue()) {
                    NotificationActionBridge.dispatch(personId, action)
                }
            }
            withCompletionHandler()
        }
    }

    init {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.delegate = delegate
        val checkInAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_CHECKIN,
            title = "Check in",
            options = UNNotificationActionOptionNone
        )
        val snoozeAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_SNOOZE,
            title = "Snooze 3d",
            options = UNNotificationActionOptionNone
        )
        val category = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_ID,
            actions = listOf(checkInAction, snoozeAction),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone
        )
        center.setNotificationCategories(setOf(category))
        center.getNotificationSettingsWithCompletionHandler { settings ->
            cachedGranted = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
        }
    }

    actual fun isSupported(): Boolean = true

    actual fun isGranted(): Boolean = cachedGranted

    actual suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            cachedGranted = granted
            if (cont.isActive) {
                cont.resume(granted)
            }
        }
    }

    actual fun syncSchedule(items: List<PlatformReminderItem>, overdueCount: Int) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()

        UIApplication.sharedApplication.setApplicationIconBadgeNumber(overdueCount.toLong())

        val now = currentTimeMillis()
        for (item in items) {
            val delaySeconds = ((item.at - now) / 1000.0).coerceAtLeast(1.0)
            val content = UNMutableNotificationContent().apply {
                setTitle(item.title)
                setBody(item.body)
                setSound(UNNotificationSound.defaultSound())
                setCategoryIdentifier(CATEGORY_ID)
                setUserInfo(mapOf("personId" to item.personId))
            }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(delaySeconds, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier(item.id, content, trigger)
            center.addNotificationRequest(request, null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual fun shareJsonFile(filename: String, jsonContent: String) {
    try {
        val tempDir = NSTemporaryDirectory().trimEnd('/')
        val filePath = "$tempDir/$filename"
        NSString.create(string = jsonContent).writeToFile(filePath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        val fileUrl = NSURL.fileURLWithPath(filePath)

        val activityController = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null
        )
        val rootVc = getRootViewController()
        activityController.popoverPresentationController?.sourceView = rootVc?.view
        rootVc?.presentViewController(activityController, animated = true, completion = null)
    } catch (_: Throwable) {
    }
}

actual fun openExternalUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

private class WebAuthContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        val app = UIApplication.sharedApplication
        return app.keyWindow ?: (app.windows.firstOrNull() as? UIWindow) ?: UIWindow()
    }
}

private var activeAuthSession: ASWebAuthenticationSession? = null
private var activeAuthContextProvider: WebAuthContextProvider? = null

actual suspend fun launchGoogleAuthFlow(authUrl: String, callbackScheme: String): String =
    suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(authUrl)
        if (nsUrl == null) {
            cont.resumeWithException(IllegalArgumentException("Invalid auth URL"))
            return@suspendCancellableCoroutine
        }

        dispatch_async(dispatch_get_main_queue()) {
            val provider = WebAuthContextProvider()
            val session = ASWebAuthenticationSession(
                uRL = nsUrl,
                callbackURLScheme = callbackScheme
            ) { callbackURL, error ->
                activeAuthSession = null
                activeAuthContextProvider = null
                if (!cont.isActive) return@ASWebAuthenticationSession
                val urlStr = callbackURL?.absoluteString
                if (!urlStr.isNullOrBlank()) {
                    cont.resume(urlStr)
                } else {
                    val desc = error?.localizedDescription ?: "Sign-in cancelled"
                    cont.resumeWithException(Exception(desc))
                }
            }
            session.presentationContextProvider = provider
            session.prefersEphemeralWebBrowserSession = false
            activeAuthContextProvider = provider
            activeAuthSession = session

            cont.invokeOnCancellation {
                session.cancel()
                activeAuthSession = null
                activeAuthContextProvider = null
            }

            if (!session.start()) {
                activeAuthSession = null
                activeAuthContextProvider = null
                if (cont.isActive) {
                    cont.resumeWithException(Exception("Could not start authentication session"))
                }
            }
        }
    }

actual suspend fun signInWithGoogleNative(serverClientId: String): String? = null

actual fun isAppleSignInSupported(): Boolean = true

private class AppleSignInDelegate(
    private val onComplete: (Result<ASAuthorizationAppleIDCredential>) -> Unit
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization: ASAuthorization) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        onComplete(
            if (credential != null) Result.success(credential)
            else Result.failure(Exception("Unexpected Apple credential"))
        )
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        val message = if (didCompleteWithError.code == ASAuthorizationErrorCanceled) {
            "Sign-in cancelled"
        } else {
            didCompleteWithError.localizedDescription
        }
        onComplete(Result.failure(Exception(message)))
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor {
        val app = UIApplication.sharedApplication
        return app.keyWindow ?: (app.windows.firstOrNull() as? UIWindow) ?: UIWindow()
    }
}

private var activeAppleSignInDelegate: AppleSignInDelegate? = null

@OptIn(ExperimentalForeignApi::class)
private fun randomNonce(length: Int = 32): String {
    val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._"
    val bytes = ByteArray(length)
    val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, length.convert(), it.addressOf(0)) }
    check(status == 0) { "Could not generate nonce" }
    return bytes.joinToString("") { charset[(it.toInt() and 0xFF) % charset.length].toString() }
}

@OptIn(ExperimentalForeignApi::class)
private fun sha256Hex(input: String): String {
    val data = input.encodeToByteArray()
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { src ->
        digest.usePinned { dst ->
            CC_SHA256(src.addressOf(0), data.size.convert(), dst.addressOf(0))
        }
    }
    return digest.joinToString("") { it.toString(16).padStart(2, '0') }
}

@OptIn(BetaInteropApi::class)
actual suspend fun signInWithAppleNative(): AppleSignInCredential {
    val rawNonce = randomNonce()
    val credential = suspendCancellableCoroutine { cont ->
        dispatch_async(dispatch_get_main_queue()) {
            val request = ASAuthorizationAppleIDProvider().createRequest().apply {
                requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
                nonce = sha256Hex(rawNonce)
            }
            val delegate = AppleSignInDelegate { result ->
                activeAppleSignInDelegate = null
                if (cont.isActive) cont.resumeWith(result)
            }
            activeAppleSignInDelegate = delegate
            ASAuthorizationController(authorizationRequests = listOf(request)).apply {
                this.delegate = delegate
                presentationContextProvider = delegate
                performRequests()
            }
        }
    }

    val tokenData = credential.identityToken ?: throw Exception("Missing Apple identity token")
    val idToken = NSString.create(data = tokenData, encoding = NSUTF8StringEncoding)?.toString()
        ?: throw Exception("Invalid Apple identity token")
    val fullName = credential.fullName?.let {
        NSPersonNameComponentsFormatter.localizedStringFromPersonNameComponents(it, style = 0, options = 0u)
    }
    val authorizationCode = credential.authorizationCode?.let {
        NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString()
    }
    return AppleSignInCredential(
        idToken = idToken,
        rawNonce = rawNonce,
        fullName = fullName,
        authorizationCode = authorizationCode,
    )
}

actual fun appleLogoBitmap(): ImageBitmap? {
    val config = UIImageSymbolConfiguration.configurationWithPointSize(64.0)
    val symbol = UIImage.systemImageNamed("apple.logo", withConfiguration = config) ?: return null
    val black = symbol.imageWithTintColor(UIColor.blackColor, renderingMode = UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal)
    val data = UIImagePNGRepresentation(black) ?: return null
    return decodeImageBitmap(data.toByteArray())
}

private class ContactPickerDelegate(
    private val onPicked: (List<PickedContact>) -> Unit
) : NSObject(), CNContactPickerDelegateProtocol {
    override fun contactPicker(picker: CNContactPickerViewController, didSelectContacts: List<*>) {
        val pickedList = mutableListOf<PickedContact>()
        for (item in didSelectContacts) {
            val contact = item as? CNContact ?: continue
            val given = contact.givenName
            val family = contact.familyName
            val fullName = listOf(given, family).filter { it.isNotBlank() }.joinToString(" ")
            val name = fullName.ifBlank { "Contact" }

            val postal = (contact.postalAddresses.firstOrNull() as? CNLabeledValue)?.value as? CNPostalAddress
                ?: (contact.postalAddresses.firstOrNull() as? CNPostalAddress)
            val city = postal?.city?.takeIf { it.isNotBlank() }

            pickedList.add(PickedContact(name = name, city = city))
        }
        if (pickedList.isNotEmpty()) {
            onPicked(pickedList)
        }
    }

    override fun contactPicker(picker: CNContactPickerViewController, didSelectContact: CNContact) {
        val given = didSelectContact.givenName
        val family = didSelectContact.familyName
        val fullName = listOf(given, family).filter { it.isNotBlank() }.joinToString(" ")
        val name = fullName.ifBlank { "Contact" }

        val postal = (didSelectContact.postalAddresses.firstOrNull() as? CNLabeledValue)?.value as? CNPostalAddress
            ?: (didSelectContact.postalAddresses.firstOrNull() as? CNPostalAddress)
        val city = postal?.city?.takeIf { it.isNotBlank() }

        onPicked(listOf(PickedContact(name = name, city = city)))
    }
}

@Composable
actual fun rememberContactPickerLauncher(onPicked: (List<PickedContact>) -> Unit): () -> Unit {
    val delegate = remember(onPicked) { ContactPickerDelegate(onPicked) }
    return remember(delegate) {
        {
            val picker = CNContactPickerViewController().apply {
                this.delegate = delegate
            }
            val rootVc = getRootViewController()
            rootVc?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PhotoPickerDelegate(
    private val onPicked: (ByteArray) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    @OptIn(ExperimentalForeignApi::class)
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        val provider = result.itemProvider
        val typeId = UTTypeImage.identifier
        if (!provider.hasItemConformingToTypeIdentifier(typeId)) return
        provider.loadDataRepresentationForTypeIdentifier(typeId) { data, _ ->
            val raw = data ?: return@loadDataRepresentationForTypeIdentifier
            val image = UIImage.imageWithData(raw) ?: return@loadDataRepresentationForTypeIdentifier
            val jpegBytes = encodeSquareAvatarJpeg(image) ?: return@loadDataRepresentationForTypeIdentifier
            dispatch_async(dispatch_get_main_queue()) {
                onPicked(jpegBytes)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun cropSquareAvatarJpeg(
    sourceBytes: ByteArray,
    leftFrac: Float,
    topFrac: Float,
    widthFrac: Float,
    heightFrac: Float,
): ByteArray? {
    val nsData = sourceBytes.toNSData() ?: return null
    val image = UIImage.imageWithData(nsData) ?: return null
    val (w, h) = image.size.useContents { width to height }
    if (w <= 0.0 || h <= 0.0) return null
    val rawW = (widthFrac.toDouble() * w).coerceIn(1.0, w)
    val rawH = (heightFrac.toDouble() * h).coerceIn(1.0, h)
    val cropSide = min(rawW, rawH)
    val cropX = (leftFrac.toDouble() * w).coerceIn(0.0, (w - cropSide).coerceAtLeast(0.0))
    val cropY = (topFrac.toDouble() * h).coerceIn(0.0, (h - cropSide).coerceAtLeast(0.0))
    val target = min(cropSide, 512.0)
    val scale = target / cropSide

    val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
        this.scale = 1.0
    }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(target, target), format = format)
    val squared = renderer.imageWithActions { _ ->
        image.drawInRect(CGRectMake(-cropX * scale, -cropY * scale, w * scale, h * scale))
    }
    val data = UIImageJPEGRepresentation(squared, 0.85) ?: return null
    return data.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun encodeSquareAvatarJpeg(image: UIImage): ByteArray? {
    val (origW, origH) = image.size.useContents { width to height }
    if (origW <= 0.0 || origH <= 0.0) return null
    val maxSide = max(origW, origH)
    val scale = if (maxSide > 1600.0) 1600.0 / maxSide else 1.0
    val drawW = (origW * scale).coerceAtLeast(1.0)
    val drawH = (origH * scale).coerceAtLeast(1.0)

    val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
        this.scale = 1.0
    }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(drawW, drawH), format = format)
    val normalized = renderer.imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, drawW, drawH))
    }
    val data = UIImageJPEGRepresentation(normalized, 0.92) ?: return null
    return data.toByteArray()
}

@Composable
actual fun rememberPhotoPickerLauncher(onPicked: (ByteArray) -> Unit): () -> Unit {
    val delegate = remember(onPicked) { PhotoPickerDelegate(onPicked) }
    return remember(delegate) {
        {
            val config = PHPickerConfiguration().apply {
                selectionLimit = 1
                filter = PHPickerFilter.imagesFilter()
            }
            val picker = PHPickerViewController(configuration = config).apply {
                this.delegate = delegate
            }
            getRootViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@Composable
actual fun StatusBarEffect(colorHex: Long, dimmed: Boolean) {
    SideEffect {
        val baseColor = Color(colorHex)
        val luminance = 0.299f * baseColor.red + 0.587f * baseColor.green + 0.114f * baseColor.blue
        val style = if (luminance > 0.5f) {
            UIStatusBarStyleDarkContent
        } else {
            UIStatusBarStyleLightContent
        }
        UIApplication.sharedApplication.setStatusBarStyle(style, animated = true)
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberNativeDatePicker(
    initialDateMillis: Long,
    onDateSelected: (Long) -> Unit,
): () -> Unit {
    return remember(initialDateMillis, onDateSelected) {
        {
            val rootVc = getRootViewController()
            if (rootVc != null) {
                val vc = UIViewController()
                vc.view.backgroundColor = UIColor.whiteColor
                vc.modalPresentationStyle = UIModalPresentationPageSheet

                val picker = UIDatePicker().apply {
                    datePickerMode = UIDatePickerMode.UIDatePickerModeDate
                    preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleInline
                    date = NSDate.dateWithTimeIntervalSince1970(initialDateMillis / 1000.0)
                    translatesAutoresizingMaskIntoConstraints = false
                }

                val cancelButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
                    setTitle("Cancel", forState = 0u)
                    translatesAutoresizingMaskIntoConstraints = false
                    addAction(
                        UIAction.actionWithHandler { _ ->
                            vc.dismissViewControllerAnimated(true, completion = null)
                        },
                        forControlEvents = UIControlEventTouchUpInside
                    )
                }

                val doneButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
                    setTitle("Done", forState = 0u)
                    translatesAutoresizingMaskIntoConstraints = false
                    addAction(
                        UIAction.actionWithHandler { _ ->
                            val millis = (picker.date.timeIntervalSince1970 * 1000.0).toLong()
                            val tz = TimeZone.currentSystemDefault()
                            val localDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
                            val noonMillis = LocalDateTime(localDate.year, localDate.monthNumber, localDate.dayOfMonth, 12, 0, 0, 0)
                                .toInstant(tz)
                                .toEpochMilliseconds()
                            vc.dismissViewControllerAnimated(true) {
                                onDateSelected(noonMillis)
                            }
                        },
                        forControlEvents = UIControlEventTouchUpInside
                    )
                }

                vc.view.addSubview(cancelButton)
                vc.view.addSubview(doneButton)
                vc.view.addSubview(picker)

                val guide = vc.view.safeAreaLayoutGuide
                NSLayoutConstraint.activateConstraints(
                    listOf(
                        cancelButton.topAnchor.constraintEqualToAnchor(guide.topAnchor, constant = 16.0),
                        cancelButton.leadingAnchor.constraintEqualToAnchor(guide.leadingAnchor, constant = 20.0),
                        doneButton.topAnchor.constraintEqualToAnchor(guide.topAnchor, constant = 16.0),
                        doneButton.trailingAnchor.constraintEqualToAnchor(guide.trailingAnchor, constant = -20.0),
                        picker.topAnchor.constraintEqualToAnchor(doneButton.bottomAnchor, constant = 12.0),
                        picker.leadingAnchor.constraintEqualToAnchor(guide.leadingAnchor, constant = 16.0),
                        picker.trailingAnchor.constraintEqualToAnchor(guide.trailingAnchor, constant = -16.0),
                    )
                )

                rootVc.presentViewController(vc, animated = true, completion = null)
            }
        }
    }
}

private fun getRootViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    val window = app.keyWindow ?: (app.windows.firstOrNull() as? UIWindow)
    var top = window?.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}

object IosStoreKit2Bridge {
    private var checkHandler: ((String) -> Unit)? = null
    private var hasReported = false
    private var lastReportedEntitled: Boolean = false
    private var lastReportedTxId: String? = null
    private var onEntitlementUpdate: ((Boolean, String?, String?) -> Unit)? = null

    fun registerEntitlementChecker(checker: (String) -> Unit) {
        checkHandler = checker
        if (hasReported) {
            onEntitlementUpdate?.invoke(lastReportedEntitled, "ios", lastReportedTxId)
        }
    }

    fun reportEntitlement(entitled: Boolean, transactionId: String?) {
        hasReported = true
        lastReportedEntitled = entitled
        lastReportedTxId = transactionId
        onEntitlementUpdate?.invoke(entitled, "ios", transactionId)
    }

    internal fun attachStoreObserver(callback: (Boolean, String?, String?) -> Unit) {
        onEntitlementUpdate = callback
        if (hasReported) {
            callback(lastReportedEntitled, "ios", lastReportedTxId)
        }
        checkHandler?.invoke(UNLIMITED_PRODUCT_ID)
    }

    internal fun currentActiveTransactionId(): String? =
        if (hasReported && lastReportedEntitled) lastReportedTxId else null
}

actual object PlatformBilling {
    private var entitlementCallback: ((Boolean, String?, String?) -> Unit)? = null
    private var cachedProduct: SKProduct? = null
    private var cachedFormattedPrice: String? = null
    private var activeProductsRequest: SKProductsRequest? = null
    private var activeProductsDelegate: SKProductsRequestDelegateProtocol? = null
    private var pendingPurchaseContinuation: CancellableContinuation<PurchaseOutcome>? = null
    private var pendingPurchaseProductId: String = UNLIMITED_PRODUCT_ID
    private var pendingRestoreContinuation: CancellableContinuation<RestoreOutcome>? = null
    private var pendingRestoreProductId: String = UNLIMITED_PRODUCT_ID
    private var restoreMatched: Boolean = false
    private var restoreMatchedTxId: String? = null
    private var observerRegistered: Boolean = false

    private val transactionObserver = object : NSObject(), SKPaymentTransactionObserverProtocol {
        override fun paymentQueue(queue: SKPaymentQueue, updatedTransactions: List<*>) {
            for (item in updatedTransactions) {
                val tx = item as? SKPaymentTransaction ?: continue
                val txProductId = tx.payment.productIdentifier
                when (tx.transactionState) {
                    SKPaymentTransactionState.SKPaymentTransactionStatePurchased -> {
                        val txId = tx.originalTransaction?.transactionIdentifier ?: tx.transactionIdentifier
                        queue.finishTransaction(tx)
                        if (txProductId == UNLIMITED_PRODUCT_ID || txProductId == pendingPurchaseProductId) {
                            entitlementCallback?.invoke(true, "ios", txId)
                            dispatch_async(dispatch_get_main_queue()) {
                                val cont = pendingPurchaseContinuation
                                pendingPurchaseContinuation = null
                                if (cont != null && cont.isActive) {
                                    cont.resume(PurchaseOutcome.Success(txProductId, platform = "ios", purchaseToken = txId))
                                }
                            }
                        }
                    }
                    SKPaymentTransactionState.SKPaymentTransactionStateRestored -> {
                        val txId = tx.originalTransaction?.transactionIdentifier ?: tx.transactionIdentifier
                        queue.finishTransaction(tx)
                        if (txProductId == pendingRestoreProductId || txProductId == UNLIMITED_PRODUCT_ID) {
                            restoreMatched = true
                            restoreMatchedTxId = txId
                            if (pendingRestoreContinuation == null) {
                                entitlementCallback?.invoke(true, "ios", txId)
                            }
                        }
                        if (txProductId == pendingPurchaseProductId) {
                            dispatch_async(dispatch_get_main_queue()) {
                                val cont = pendingPurchaseContinuation
                                pendingPurchaseContinuation = null
                                if (cont != null && cont.isActive) {
                                    cont.resume(PurchaseOutcome.Success(txProductId, platform = "ios", purchaseToken = txId))
                                }
                            }
                        }
                    }
                    SKPaymentTransactionState.SKPaymentTransactionStateFailed -> {
                        val err = tx.error
                        val cancelled = err?.code == SKErrorCode.SKErrorPaymentCancelled.value
                        val message = err?.localizedDescription ?: "Purchase could not be completed."
                        queue.finishTransaction(tx)
                        if (txProductId == pendingPurchaseProductId) {
                            dispatch_async(dispatch_get_main_queue()) {
                                val cont = pendingPurchaseContinuation
                                pendingPurchaseContinuation = null
                                if (cont != null && cont.isActive) {
                                    if (cancelled) {
                                        cont.resume(PurchaseOutcome.Cancelled)
                                    } else {
                                        cont.resume(PurchaseOutcome.Error(message))
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }

        override fun paymentQueueRestoreCompletedTransactionsFinished(queue: SKPaymentQueue) {
            val matched = restoreMatched
            val txId = restoreMatchedTxId
            dispatch_async(dispatch_get_main_queue()) {
                val cont = pendingRestoreContinuation
                pendingRestoreContinuation = null
                if (cont != null && cont.isActive) {
                    cont.resume(
                        if (matched) RestoreOutcome.Found("ios", txId) else RestoreOutcome.NotFound
                    )
                }
            }
        }

        override fun paymentQueue(
            queue: SKPaymentQueue,
            restoreCompletedTransactionsFailedWithError: NSError
        ) {
            dispatch_async(dispatch_get_main_queue()) {
                val cont = pendingRestoreContinuation
                pendingRestoreContinuation = null
                if (cont != null && cont.isActive) {
                    cont.resume(RestoreOutcome.NotFound)
                }
            }
        }
    }

    actual fun initialize(onEntitlementVerified: (verified: Boolean, platform: String?, purchaseToken: String?) -> Unit) {
        entitlementCallback = onEntitlementVerified
        if (!observerRegistered) {
            observerRegistered = true
            dispatch_async(dispatch_get_main_queue()) {
                SKPaymentQueue.defaultQueue().addTransactionObserver(transactionObserver)
            }
        }
        IosStoreKit2Bridge.attachStoreObserver(onEntitlementVerified)
    }

    actual suspend fun queryProduct(productId: String): BillingProductInfo {
        val product = fetchProduct(productId)
        val price = product?.let { formatProductPrice(it) }
        if (!price.isNullOrBlank()) {
            cachedFormattedPrice = price
        }
        return BillingProductInfo(
            productId = productId,
            formattedPrice = price ?: cachedFormattedPrice ?: DEFAULT_UNLIMITED_PRICE,
            isStoreConnected = product != null
        )
    }

    actual suspend fun purchaseUnlimited(productId: String): PurchaseOutcome {
        pendingPurchaseProductId = productId
        val product = cachedProduct?.takeIf { it.productIdentifier == productId } ?: fetchProduct(productId)
        if (product != null && SKPaymentQueue.canMakePayments()) {
            return suspendCancellableCoroutine { cont ->
                pendingPurchaseContinuation?.cancel()
                pendingPurchaseContinuation = cont
                cont.invokeOnCancellation {
                    if (pendingPurchaseContinuation === cont) {
                        pendingPurchaseContinuation = null
                    }
                }
                dispatch_async(dispatch_get_main_queue()) {
                    val payment = SKPayment.paymentWithProduct(product)
                    SKPaymentQueue.defaultQueue().addPayment(payment)
                }
            }
        }

        val priceLabel = cachedFormattedPrice ?: DEFAULT_UNLIMITED_PRICE
        return showFallbackPurchaseAlert(productId, priceLabel)
    }

    actual suspend fun restorePurchases(productId: String): RestoreOutcome {
        IosStoreKit2Bridge.currentActiveTransactionId()?.let { txId ->
            return RestoreOutcome.Found(platform = "ios", purchaseToken = txId)
        }
        pendingRestoreProductId = productId
        restoreMatched = false
        restoreMatchedTxId = null
        return withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine { cont ->
                pendingRestoreContinuation?.cancel()
                pendingRestoreContinuation = cont
                cont.invokeOnCancellation {
                    if (pendingRestoreContinuation === cont) {
                        pendingRestoreContinuation = null
                    }
                }
                dispatch_async(dispatch_get_main_queue()) {
                    SKPaymentQueue.defaultQueue().restoreCompletedTransactions()
                }
            }
        } ?: RestoreOutcome.NotFound
    }

    private suspend fun fetchProduct(productId: String): SKProduct? {
        cachedProduct?.takeIf { it.productIdentifier == productId }?.let { return it }
        return withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { cont ->
                dispatch_async(dispatch_get_main_queue()) {
                    val request = SKProductsRequest(productIdentifiers = setOf(productId))
                    val delegate = object : NSObject(), SKProductsRequestDelegateProtocol {
                        override fun productsRequest(
                            request: SKProductsRequest,
                            didReceiveResponse: SKProductsResponse
                        ) {
                            activeProductsRequest = null
                            activeProductsDelegate = null
                            val found = didReceiveResponse.products
                                .mapNotNull { it as? SKProduct }
                                .firstOrNull { it.productIdentifier == productId }
                            if (found != null) {
                                cachedProduct = found
                            }
                            if (cont.isActive) {
                                cont.resume(found)
                            }
                        }

                        override fun request(request: SKRequest, didFailWithError: NSError) {
                            activeProductsRequest = null
                            activeProductsDelegate = null
                            if (cont.isActive) {
                                cont.resume(null)
                            }
                        }
                    }
                    activeProductsRequest = request
                    activeProductsDelegate = delegate
                    request.delegate = delegate
                    cont.invokeOnCancellation {
                        request.cancel()
                        activeProductsRequest = null
                        activeProductsDelegate = null
                    }
                    request.start()
                }
            }
        }
    }

    private fun formatProductPrice(product: SKProduct): String? {
        val formatter = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterCurrencyStyle
            locale = product.priceLocale
        }
        return formatter.stringFromNumber(product.price)
    }

    private suspend fun showFallbackPurchaseAlert(
        productId: String,
        priceLabel: String,
    ): PurchaseOutcome = suspendCancellableCoroutine { cont ->
        dispatch_async(dispatch_get_main_queue()) {
            val rootVc = getRootViewController()
            if (rootVc == null) {
                if (cont.isActive) {
                    cont.resume(PurchaseOutcome.Error("Purchase dialog could not be presented."))
                }
                return@dispatch_async
            }
            val alert = UIAlertController.alertControllerWithTitle(
                title = "Spheres Unlimited",
                message = "One-Time Purchase · $priceLabel\n\nUnlock unlimited connections and spheres forever across all your devices.",
                preferredStyle = UIAlertControllerStyleAlert
            )
            alert.addAction(
                UIAlertAction.actionWithTitle(
                    title = "Purchase · $priceLabel",
                    style = UIAlertActionStyleDefault
                ) { _ ->
                    if (cont.isActive) {
                        entitlementCallback?.invoke(true, "debug_fallback", null)
                        cont.resume(PurchaseOutcome.Success(productId, platform = "debug_fallback"))
                    }
                }
            )
            alert.addAction(
                UIAlertAction.actionWithTitle(
                    title = "Cancel",
                    style = UIAlertActionStyleCancel
                ) { _ ->
                    if (cont.isActive) {
                        cont.resume(PurchaseOutcome.Cancelled)
                    }
                }
            )
            cont.invokeOnCancellation {
                alert.dismissViewControllerAnimated(true, completion = null)
            }
            rootVc.presentViewController(alert, animated = true, completion = null)
        }
    }
}
