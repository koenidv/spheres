package de.koenidv.spheres.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Contacts.CNContact
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPostalAddress
import platform.ContactsUI.CNContactPickerDelegateProtocol
import platform.ContactsUI.CNContactPickerViewController
import platform.Foundation.NSDate
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.UIKit.setStatusBarStyle
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

actual object PlatformNotifications {
    private var cachedGranted: Boolean = false

    init {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
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

private fun getRootViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    val window = app.keyWindow ?: (app.windows.firstOrNull() as? UIWindow)
    return window?.rootViewController
}
