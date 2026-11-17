package de.koenidv.spheres.data

import de.koenidv.spheres.BuildFlags
import de.koenidv.spheres.domain.AppUser
import de.koenidv.spheres.domain.AvatarConfig
import de.koenidv.spheres.domain.BillingEntitlement
import de.koenidv.spheres.domain.Birthday
import de.koenidv.spheres.domain.DAY
import de.koenidv.spheres.domain.DEFAULT_UNLIMITED_PRICE
import de.koenidv.spheres.domain.Entry
import de.koenidv.spheres.domain.EntryKind
import de.koenidv.spheres.domain.EventLimitStatus
import de.koenidv.spheres.domain.FREE_CONNECTIONS_LIMIT
import de.koenidv.spheres.domain.InteractionKind
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.Quality
import de.koenidv.spheres.domain.SPHERE_COLORS
import de.koenidv.spheres.domain.SocialEvent
import de.koenidv.spheres.domain.Sphere
import de.koenidv.spheres.domain.UNLIMITED_PRODUCT_ID
import de.koenidv.spheres.domain.at9
import de.koenidv.spheres.domain.canAddPerson
import de.koenidv.spheres.domain.canCreateSphere
import de.koenidv.spheres.domain.evaluateEventLimitStatus
import de.koenidv.spheres.domain.firstName
import de.koenidv.spheres.domain.formatDay
import de.koenidv.spheres.domain.parseQuickAdd
import de.koenidv.spheres.domain.syncReminderSchedule
import de.koenidv.spheres.domain.toEntryKind
import de.koenidv.spheres.domain.wasEventCreatedBeforeLimit
import de.koenidv.spheres.platform.NotificationActionBridge
import de.koenidv.spheres.platform.NotificationActionType
import de.koenidv.spheres.platform.PlatformBilling
import de.koenidv.spheres.platform.PlatformNotifications
import de.koenidv.spheres.platform.PlatformStorage
import de.koenidv.spheres.platform.PurchaseOutcome
import de.koenidv.spheres.platform.RestoreOutcome
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.platform.haptic
import de.koenidv.spheres.platform.randomUuid
import de.koenidv.spheres.platform.signInWithAppleNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface PaywallReason {
    data object ConnectionsLimit : PaywallReason
    data object SpheresLimit : PaywallReason
    data class EventGraceEnded(val eventName: String? = null) : PaywallReason
    data object Settings : PaywallReason
}

sealed interface SheetState {
    data object Action : SheetState
    data class AddPerson(
        val intervalDays: Int? = 30,
        val sphereId: String? = null,
        val name: String? = null,
        val note: String? = null,
    ) : SheetState
    data class SphereEdit(val sphereId: String? = null) : SheetState
    data class EventEdit(val eventId: String) : SheetState
    data class AvatarEdit(val personId: String) : SheetState
    data class PickPerson(val then: LogMode) : SheetState
    data class Log(val personId: String, val mode: LogMode) : SheetState
    data class Paywall(
        val reason: PaywallReason = PaywallReason.Settings,
        val returnTo: SheetState? = null,
    ) : SheetState
}

enum class LogMode { INTERACTION, NOTE }

data class ToastData(val id: Long, val text: String, val undo: (() -> Unit)? = null)

data class PendingTransfer(
    val platform: String,
    val productId: String,
    val purchaseToken: String,
    val returnTo: SheetState? = null,
)

data class AppState(
    val authReady: Boolean = false,
    val user: AppUser? = null,
    val dataReady: Boolean = false,
    val spheres: List<Sphere> = emptyList(),
    val events: List<SocialEvent> = emptyList(),
    val people: List<Person> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val paywallEnabled: Boolean = BuildFlags.PAYWALL_ENABLED,
    val isUnlimited: Boolean = !BuildFlags.PAYWALL_ENABLED,
    val unlimitedPrice: String = DEFAULT_UNLIMITED_PRICE,
    val isPurchasing: Boolean = false,
    val pendingTransfer: PendingTransfer? = null,
    val sheet: SheetState? = null,
    val toast: ToastData? = null,
)

object SpheresStore {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val backend = FirestoreBackend()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var toastJob: Job? = null
    private var toastCounter = 0L

    init {
        loadCachedAuthAndData()
        if (BuildFlags.PAYWALL_ENABLED) {
            PlatformBilling.initialize { verified, platform, purchaseToken ->
                if (verified) {
                    val u = state.value.user
                    if (u != null && state.value.isUnlimited) {
                        grantUnlimited(saveRemote = true, platform = platform, purchaseToken = purchaseToken)
                    }
                } else {
                    revokeUnlimitedIfOwnedByPlatform(platform)
                }
            }
            scope.launch {
                val info = PlatformBilling.queryProduct(UNLIMITED_PRODUCT_ID)
                _state.update { it.copy(unlimitedPrice = info.formattedPrice) }
            }
        }
        NotificationActionBridge.registerHandler { personId, action ->
            scope.launch {
                when (action) {
                    NotificationActionType.CHECKIN -> quickCheckIn(personId)
                    NotificationActionType.SNOOZE -> snooze(personId, 3)
                }
            }
        }
    }

    private fun loadCachedAuthAndData() {
        val userStr = PlatformStorage.getString("auth_user")
        if (!userStr.isNullOrBlank()) {
            try {
                val user = json.decodeFromString<AppUser>(userStr)
                if (user.uid != "default_user" && !user.idToken.isNullOrBlank()) {
                    _state.update { it.copy(user = user, authReady = true) }
                    loadCachedDataForUser(user.uid)
                    scope.launch { syncRemote(user.uid) }
                    return
                } else {
                    PlatformStorage.putString("auth_user", null)
                }
            } catch (_: Exception) {
                PlatformStorage.putString("auth_user", null)
            }
        }
        _state.update { it.copy(authReady = true) }
    }

    private fun loadCachedDataForUser(uid: String) {
        val sStr = PlatformStorage.getString("cache_${uid}_spheres")
        val eStr = PlatformStorage.getString("cache_${uid}_events")
        val pStr = PlatformStorage.getString("cache_${uid}_people")
        val entStr = PlatformStorage.getString("cache_${uid}_entries")
        val cachedUnlimited = PlatformStorage.getString("cache_${uid}_unlimited") == "true"
        if (cachedUnlimited || !BuildFlags.PAYWALL_ENABLED) {
            _state.update { it.copy(isUnlimited = true) }
        } else {
            _state.update { it.copy(isUnlimited = false) }
        }

        val spheres = if (!sStr.isNullOrBlank()) try { json.decodeFromString<List<Sphere>>(sStr) } catch (_: Exception) { emptyList() } else emptyList()
        val events = if (!eStr.isNullOrBlank()) try { json.decodeFromString<List<SocialEvent>>(eStr) } catch (_: Exception) { emptyList() } else emptyList()
        val people = if (!pStr.isNullOrBlank()) try { json.decodeFromString<List<Person>>(pStr) } catch (_: Exception) { emptyList() } else emptyList()
        val entries = if (!entStr.isNullOrBlank()) try { json.decodeFromString<List<Entry>>(entStr) } catch (_: Exception) { emptyList() } else emptyList()

        val hasData = spheres.isNotEmpty() || events.isNotEmpty() || people.isNotEmpty() || entries.isNotEmpty()
        if (hasData) {
            val sortedSpheres = sortSpheres(spheres)
            val sortedEvents = sortEvents(events)
            val normPeople = normalizePeople(people)
            _state.update {
                it.copy(
                    spheres = sortedSpheres,
                    events = sortedEvents,
                    people = normPeople,
                    entries = entries,
                    dataReady = true
                )
            }
            syncReminderSchedule(normPeople, entries)
        }
    }

    private fun persistLocalData(
        uid: String,
        spheres: List<Sphere>,
        events: List<SocialEvent>,
        people: List<Person>,
        entries: List<Entry>
    ) {
        PlatformStorage.putString("cache_${uid}_spheres", json.encodeToString(spheres))
        PlatformStorage.putString("cache_${uid}_events", json.encodeToString(events))
        PlatformStorage.putString("cache_${uid}_people", json.encodeToString(people))
        PlatformStorage.putString("cache_${uid}_entries", json.encodeToString(entries))
    }

    private suspend fun syncRemote(uid: String) {
        val currentUser = state.value.user ?: return
        var token = currentUser.idToken
        if (!currentUser.refreshToken.isNullOrBlank()) {
            val refreshed = backend.refreshAuthToken(currentUser.refreshToken)
            if (refreshed != null) {
                token = refreshed.first
                val updatedUser = currentUser.copy(idToken = refreshed.first, refreshToken = refreshed.second)
                PlatformStorage.putString("auth_user", json.encodeToString(updatedUser))
                _state.update { it.copy(user = updatedUser) }
            }
        }
        val remoteSpheres = backend.fetchSpheres(uid, token)
        val remoteEvents = backend.fetchEvents(uid, token)
        val remotePeople = backend.fetchPeople(uid, token)
        val remoteEntries = backend.fetchEntries(uid, token)
        if (BuildFlags.PAYWALL_ENABLED) {
            val remoteEntitlement = backend.fetchEntitlement(uid, token)

            if (remoteEntitlement?.unlocked == true) {
                PlatformStorage.putString("cache_${uid}_unlimited", "true")
                remoteEntitlement.platform?.let { p ->
                    PlatformStorage.putString("cache_${uid}_billing_platform", p)
                }
                remoteEntitlement.purchaseToken?.let { t ->
                    PlatformStorage.putString("cache_${uid}_billing_token", t)
                }
                _state.update { it.copy(isUnlimited = true) }
            } else if (remoteEntitlement != null && !remoteEntitlement.unlocked && remoteEntitlement.revokedAt != null) {
                revokeUnlimitedLocal(uid)
            } else if (state.value.isUnlimited) {
                backend.saveEntitlement(
                    uid,
                    BillingEntitlement(
                        unlocked = true,
                        unlockedAt = currentTimeMillis(),
                        productId = UNLIMITED_PRODUCT_ID,
                        platform = PlatformStorage.getString("cache_${uid}_billing_platform"),
                        purchaseToken = PlatformStorage.getString("cache_${uid}_billing_token"),
                    ),
                    token
                )
            }
        }

        val hasRemote = remoteSpheres.isNotEmpty() || remoteEvents.isNotEmpty() || remotePeople.isNotEmpty() || remoteEntries.isNotEmpty()
        if (hasRemote || !state.value.dataReady) {
            val sortedSpheres = sortSpheres(remoteSpheres)
            val sortedEvents = sortEvents(remoteEvents)
            val normPeople = normalizePeople(remotePeople)
            persistLocalData(uid, sortedSpheres, sortedEvents, normPeople, remoteEntries)
            _state.update {
                it.copy(
                    spheres = sortedSpheres,
                    events = sortedEvents,
                    people = normPeople,
                    entries = remoteEntries,
                    dataReady = true
                )
            }
            migrate()
            syncReminderSchedule(normPeople, remoteEntries)
        }
    }

    private fun migrate() {
        migrateEvents()
        migrateCity()
    }

    private fun migrateCity() {
        val people = state.value.people
        for (p in people) {
            val fact = p.facts.firstOrNull { it.label.trim().equals("city", ignoreCase = true) } ?: continue
            val updated = p.copy(
                city = if (p.city.isNullOrBlank()) fact.value.trim() else p.city,
                facts = p.facts.filter { it != fact }
            )
            updatePerson(p.id) { updated }
        }
    }

    private fun migrateEvents() {
        val uid = state.value.user?.uid ?: return
        val spheres = state.value.spheres
        val legacy = spheres.filter { it.kind == "event" }
        if (legacy.isEmpty()) return
        val legacyIds = legacy.map { it.id }.toSet()
        val existingEventIds = state.value.events.map { it.id }.toSet()
        for (s in legacy) {
            if (!existingEventIds.contains(s.id)) {
                val event = SocialEvent(
                    id = s.id,
                    name = s.name,
                    date = s.date ?: s.createdAt,
                    sphereIds = emptyList(),
                    color = s.color,
                    note = s.note,
                    createdAt = s.createdAt,
                )
                _state.update { st -> st.copy(events = sortEvents(st.events + event)) }
                scope.launch { backend.saveEvent(uid, event, state.value.user?.idToken) }
            }
        }
        for (p in state.value.people) {
            val hit = p.sphereIds.filter { legacyIds.contains(it) }
            if (hit.isNotEmpty()) {
                val updated = p.copy(
                    sphereIds = p.sphereIds.filter { !legacyIds.contains(it) },
                    eventIds = (p.eventIds + hit).distinct()
                )
                updatePerson(p.id) { updated }
            }
        }
        for (s in legacy) {
            deleteSphere(s.id)
        }
    }

    private fun sortSpheres(list: List<Sphere>): List<Sphere> =
        list.sortedByDescending { it.createdAt }

    private fun sortEvents(list: List<SocialEvent>): List<SocialEvent> =
        list.sortedWith(compareByDescending<SocialEvent> { it.date }.thenByDescending { it.createdAt })

    private fun normalizePeople(list: List<Person>): List<Person> =
        list.map { it.copy(sphereIds = it.sphereIds, eventIds = it.eventIds) }

    suspend fun signIn(idToken: String? = null) {
        val user = if (!idToken.isNullOrBlank()) {
            backend.signInWithGoogleIdToken(idToken)
                ?: throw Exception("Google authentication failed")
        } else {
            backend.signInWithGoogle()
        }
        completeSignIn(user)
    }

    suspend fun signInWithApple() {
        completeSignIn(backend.signInWithApple())
    }

    private fun completeSignIn(user: AppUser) {
        PlatformStorage.putString("auth_user", json.encodeToString(user))
        _state.update { it.copy(user = user, authReady = true) }
        loadCachedDataForUser(user.uid)
        scope.launch { syncRemote(user.uid) }
    }

    suspend fun deleteAccount() {
        val uid = state.value.user?.uid ?: return
        val token = refreshSessionToken() ?: throw Exception("Please sign in again")
        if (backend.signInProvider(token) == "apple.com") {
            // Apple requires revoking its tokens, which needs a fresh authorization code
            val apple = signInWithAppleNative()
            apple.authorizationCode?.let { backend.revokeAppleToken(token, it) }
        }
        backend.deleteAccount(token)
        listOf("spheres", "events", "people", "entries", "unlimited", "billing_platform", "billing_token").forEach {
            PlatformStorage.putString("cache_${uid}_$it", null)
        }
        signOut()
    }

    fun signOut() {
        PlatformStorage.putString("auth_user", null)
        _state.update {
            it.copy(
                user = null,
                dataReady = false,
                spheres = emptyList(),
                events = emptyList(),
                people = emptyList(),
                entries = emptyList(),
                isUnlimited = !BuildFlags.PAYWALL_ENABLED,
                isPurchasing = false,
                pendingTransfer = null,
                sheet = null,
            )
        }
        PlatformNotifications.syncSchedule(emptyList(), 0)
    }

    fun openSheet(sheet: SheetState?) {
        if (!BuildFlags.PAYWALL_ENABLED && sheet is SheetState.Paywall) return
        val current = state.value
        val gated = when (sheet) {
            is SheetState.AddPerson -> {
                if (!canAddPerson(current.people.size, current.isUnlimited)) {
                    SheetState.Paywall(reason = PaywallReason.ConnectionsLimit, returnTo = sheet)
                } else {
                    sheet
                }
            }
            is SheetState.SphereEdit -> {
                if (sheet.sphereId == null && !canCreateSphere(current.spheres.size, current.isUnlimited)) {
                    SheetState.Paywall(reason = PaywallReason.SpheresLimit, returnTo = sheet)
                } else {
                    sheet
                }
            }
            else -> sheet
        }
        _state.update { it.copy(sheet = gated) }
    }

    fun closeSheet() {
        _state.update { it.copy(sheet = null, pendingTransfer = null) }
    }

    private fun grantUnlimited(
        saveRemote: Boolean,
        platform: String? = null,
        purchaseToken: String? = null,
    ) {
        val u = state.value.user
        val effectivePlatform = platform ?: u?.let { PlatformStorage.getString("cache_${it.uid}_billing_platform") }
        val effectiveToken = purchaseToken ?: u?.let { PlatformStorage.getString("cache_${it.uid}_billing_token") }
        u?.let {
            PlatformStorage.putString("cache_${it.uid}_unlimited", "true")
            effectivePlatform?.let { p -> PlatformStorage.putString("cache_${it.uid}_billing_platform", p) }
            effectiveToken?.let { t -> PlatformStorage.putString("cache_${it.uid}_billing_token", t) }
        }
        _state.update { it.copy(isUnlimited = true, pendingTransfer = null) }
        if (saveRemote && u != null) {
            scope.launch {
                backend.saveEntitlement(
                    u.uid,
                    BillingEntitlement(
                        unlocked = true,
                        unlockedAt = currentTimeMillis(),
                        productId = UNLIMITED_PRODUCT_ID,
                        platform = effectivePlatform,
                        purchaseToken = effectiveToken,
                        revokedAt = null,
                    ),
                    u.idToken
                )
            }
        }
    }

    private fun revokeUnlimitedLocal(uid: String? = state.value.user?.uid) {
        uid?.let {
            PlatformStorage.putString("cache_${it}_unlimited", "false")
            PlatformStorage.putString("cache_${it}_billing_platform", null)
            PlatformStorage.putString("cache_${it}_billing_token", null)
        }
        if (BuildFlags.PAYWALL_ENABLED) {
            _state.update { it.copy(isUnlimited = false) }
        }
    }

    private fun revokeUnlimitedIfOwnedByPlatform(storePlatform: String?) {
        if (!BuildFlags.PAYWALL_ENABLED || storePlatform.isNullOrBlank()) return
        val uid = state.value.user?.uid ?: return
        val storedPlatform = PlatformStorage.getString("cache_${uid}_billing_platform")
        // Preserve debug fallback entitlements and purchases owned by the other OS.
        if (storedPlatform == "debug_fallback") return
        if (storedPlatform != null && storedPlatform != storePlatform) return
        val wasUnlimited = state.value.isUnlimited || PlatformStorage.getString("cache_${uid}_unlimited") == "true"
        if (!wasUnlimited) return

        revokeUnlimitedLocal(uid)
        scope.launch {
            val u = state.value.user ?: return@launch
            backend.saveEntitlement(
                u.uid,
                BillingEntitlement(
                    unlocked = false,
                    productId = UNLIMITED_PRODUCT_ID,
                    platform = storePlatform,
                    revokedAt = currentTimeMillis(),
                ),
                u.idToken
            )
        }
    }

    private suspend fun claimOrPromptTransfer(
        platform: String?,
        productId: String,
        purchaseToken: String?,
        successMessage: String,
    ) {
        val returnTo = (state.value.sheet as? SheetState.Paywall)?.returnTo
        val u = state.value.user
        val idToken = u?.idToken

        if (platform == "debug_fallback" || platform.isNullOrBlank() || purchaseToken.isNullOrBlank() || idToken.isNullOrBlank()) {
            grantUnlimited(saveRemote = (platform == "debug_fallback"), platform = platform, purchaseToken = purchaseToken)
            _state.update { it.copy(isPurchasing = false, sheet = returnTo, pendingTransfer = null) }
            showToast(successMessage)
            return
        }

        when (val claim = backend.claimPurchase(idToken, platform, productId, purchaseToken, allowTransfer = false)) {
            BillingClaimResult.Claimed, BillingClaimResult.Transferred -> {
                grantUnlimited(saveRemote = false, platform = platform, purchaseToken = purchaseToken)
                _state.update { it.copy(isPurchasing = false, sheet = returnTo, pendingTransfer = null) }
                showToast(successMessage)
            }
            BillingClaimResult.OwnedByOther -> {
                val currentSheet = state.value.sheet as? SheetState.Paywall ?: SheetState.Paywall(reason = PaywallReason.Settings)
                _state.update {
                    it.copy(
                        isPurchasing = false,
                        sheet = currentSheet,
                        pendingTransfer = PendingTransfer(
                            platform = platform,
                            productId = productId,
                            purchaseToken = purchaseToken,
                            returnTo = returnTo,
                        )
                    )
                }
            }
            is BillingClaimResult.Error -> {
                _state.update { it.copy(isPurchasing = false) }
                showToast(claim.message)
            }
        }
    }

    fun confirmTransferPurchase() {
        val pending = state.value.pendingTransfer ?: return
        val idToken = state.value.user?.idToken ?: return
        if (state.value.isPurchasing) return
        _state.update { it.copy(isPurchasing = true) }
        scope.launch {
            try {
                when (
                    val claim = backend.claimPurchase(
                        idToken = idToken,
                        platform = pending.platform,
                        productId = pending.productId,
                        purchaseToken = pending.purchaseToken,
                        allowTransfer = true,
                    )
                ) {
                    BillingClaimResult.Claimed, BillingClaimResult.Transferred -> {
                        grantUnlimited(
                            saveRemote = false,
                            platform = pending.platform,
                            purchaseToken = pending.purchaseToken
                        )
                        _state.update { it.copy(isPurchasing = false, sheet = pending.returnTo, pendingTransfer = null) }
                        showToast("Spheres Unlimited moved to this account!")
                    }
                    BillingClaimResult.OwnedByOther -> {
                        _state.update { it.copy(isPurchasing = false) }
                        showToast("Could not transfer purchase right now")
                    }
                    is BillingClaimResult.Error -> {
                        _state.update { it.copy(isPurchasing = false) }
                        showToast(claim.message)
                    }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(isPurchasing = false) }
                showToast(e.message ?: "Could not transfer purchase")
            }
        }
    }

    fun dismissTransferPrompt() {
        _state.update { it.copy(pendingTransfer = null) }
    }

    fun purchaseUnlimited() {
        if (state.value.isPurchasing) return
        if (state.value.isUnlimited) {
            val returnTo = (state.value.sheet as? SheetState.Paywall)?.returnTo
            _state.update { it.copy(sheet = returnTo) }
            showToast("Spheres Unlimited is already active")
            return
        }
        _state.update { it.copy(isPurchasing = true, pendingTransfer = null) }
        scope.launch {
            try {
                when (val outcome = PlatformBilling.purchaseUnlimited(UNLIMITED_PRODUCT_ID)) {
                    is PurchaseOutcome.Success -> {
                        claimOrPromptTransfer(
                            platform = outcome.platform,
                            productId = outcome.productId,
                            purchaseToken = outcome.purchaseToken,
                            successMessage = "Spheres Unlimited unlocked — thank you!",
                        )
                    }
                    is PurchaseOutcome.Cancelled -> {
                        _state.update { it.copy(isPurchasing = false) }
                    }
                    is PurchaseOutcome.Error -> {
                        _state.update { it.copy(isPurchasing = false) }
                        showToast(outcome.message)
                    }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(isPurchasing = false) }
                showToast(e.message ?: "Purchase failed")
            }
        }
    }

    fun restorePurchases() {
        if (state.value.isPurchasing) return
        _state.update { it.copy(isPurchasing = true, pendingTransfer = null) }
        scope.launch {
            try {
                when (val storeRestore = PlatformBilling.restorePurchases(UNLIMITED_PRODUCT_ID)) {
                    is RestoreOutcome.Found -> {
                        claimOrPromptTransfer(
                            platform = storeRestore.platform,
                            productId = UNLIMITED_PRODUCT_ID,
                            purchaseToken = storeRestore.purchaseToken,
                            successMessage = "Spheres Unlimited restored!",
                        )
                    }
                    RestoreOutcome.NotFound -> {
                        val u = state.value.user
                        val remote = if (u != null) backend.fetchEntitlement(u.uid, u.idToken) else null
                        if (remote?.unlocked == true) {
                            grantUnlimited(
                                saveRemote = false,
                                platform = remote.platform,
                                purchaseToken = remote.purchaseToken
                            )
                            val returnTo = (state.value.sheet as? SheetState.Paywall)?.returnTo
                            _state.update { it.copy(isPurchasing = false, sheet = returnTo) }
                            showToast("Spheres Unlimited restored!")
                        } else {
                            _state.update { it.copy(isPurchasing = false) }
                            showToast("No previous purchase found")
                        }
                    }
                }
            } catch (_: Throwable) {
                _state.update { it.copy(isPurchasing = false) }
                showToast("Could not restore purchases")
            }
        }
    }

    fun showToast(text: String, undo: (() -> Unit)? = null) {
        haptic()
        toastJob?.cancel()
        val id = ++toastCounter
        _state.update { it.copy(toast = ToastData(id, text, undo)) }
        toastJob = scope.launch {
            delay(4000)
            _state.update { if (it.toast?.id == id) it.copy(toast = null) else it }
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _state.update { it.copy(toast = null) }
    }

    fun addSphere(name: String, color: String, note: String = ""): String {
        val id = randomUuid()
        val sphere = Sphere(
            id = id,
            name = name.trim(),
            color = color,
            note = note,
            createdAt = currentTimeMillis(),
        )
        _state.update { s ->
            val updated = sortSpheres(s.spheres + sphere)
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_spheres", json.encodeToString(updated)) }
            s.copy(spheres = updated)
        }
        scope.launch {
            state.value.user?.let { backend.saveSphere(it.uid, sphere, it.idToken) }
        }
        return id
    }

    fun updateSphere(id: String, name: String? = null, color: String? = null, note: String? = null) {
        val sphere = state.value.spheres.firstOrNull { it.id == id } ?: return
        val updated = sphere.copy(
            name = name?.trim() ?: sphere.name,
            color = color ?: sphere.color,
            note = note ?: sphere.note,
        )
        _state.update { s ->
            val list = s.spheres.map { if (it.id == id) updated else it }
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_spheres", json.encodeToString(list)) }
            s.copy(spheres = list)
        }
        scope.launch {
            state.value.user?.let { backend.saveSphere(it.uid, updated, it.idToken) }
        }
    }

    fun deleteSphere(id: String) {
        for (p in state.value.people.filter { it.sphereIds.contains(id) }) {
            updatePerson(p.id) { it.copy(sphereIds = it.sphereIds.filter { sId -> sId != id }) }
        }
        for (e in state.value.events.filter { it.sphereIds.contains(id) }) {
            updateEvent(e.id, sphereIds = e.sphereIds.filter { sId -> sId != id })
        }
        _state.update { s ->
            val updated = s.spheres.filter { it.id != id }
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_spheres", json.encodeToString(updated)) }
            s.copy(spheres = updated)
        }
        scope.launch {
            state.value.user?.let { backend.deleteSphere(it.uid, id, it.idToken) }
        }
    }

    fun addEvent(
        name: String? = null,
        date: Long? = null,
        sphereIds: List<String> = emptyList(),
        color: String? = null,
    ): String {
        val id = randomUuid()
        val t = currentTimeMillis()
        val eventDate = date ?: t
        val eventColor = color ?: SPHERE_COLORS[state.value.events.size % SPHERE_COLORS.size]
        val eventName = name?.trim()?.ifEmpty { null } ?: "Event · ${formatDay(eventDate)}"
        val event = SocialEvent(
            id = id,
            name = eventName,
            date = eventDate,
            sphereIds = sphereIds,
            color = eventColor,
            note = "",
            createdAt = t,
            createdBeforeLimit = state.value.people.size < FREE_CONNECTIONS_LIMIT,
        )
        _state.update { s ->
            val updated = sortEvents(s.events + event)
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_events", json.encodeToString(updated)) }
            s.copy(events = updated)
        }
        scope.launch {
            state.value.user?.let { backend.saveEvent(it.uid, event, it.idToken) }
        }
        return id
    }

    fun updateEvent(
        id: String,
        name: String? = null,
        date: Long? = null,
        sphereIds: List<String>? = null,
        color: String? = null,
        note: String? = null,
    ) {
        val event = state.value.events.firstOrNull { it.id == id } ?: return
        val updated = event.copy(
            name = name?.trim() ?: event.name,
            date = date ?: event.date,
            sphereIds = sphereIds ?: event.sphereIds,
            color = color ?: event.color,
            note = note ?: event.note,
        )
        _state.update { s ->
            val list = sortEvents(s.events.map { if (it.id == id) updated else it })
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_events", json.encodeToString(list)) }
            s.copy(events = list)
        }
        scope.launch {
            state.value.user?.let { backend.saveEvent(it.uid, updated, it.idToken) }
        }
    }

    fun deleteEvent(id: String, deleteUntrackedMembers: Boolean = false) {
        for (p in state.value.people.filter { it.eventIds.contains(id) }) {
            val rest = p.eventIds.filter { it != id }
            if (deleteUntrackedMembers && p.intervalDays == null && rest.isEmpty() && p.sphereIds.isEmpty()) {
                deletePerson(p.id)
            } else {
                updatePerson(p.id) { it.copy(eventIds = rest) }
            }
        }
        _state.update { s ->
            val updated = s.events.filter { it.id != id }
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_events", json.encodeToString(updated)) }
            s.copy(events = updated)
        }
        scope.launch {
            state.value.user?.let { backend.deleteEvent(it.uid, id, it.idToken) }
        }
    }

    fun addPerson(
        name: String,
        intervalDays: Int? = null,
        sphereIds: List<String> = emptyList(),
        eventIds: List<String> = emptyList(),
        baseContactAt: Long? = null,
        birthday: Birthday? = null,
        note: String = "",
        city: String? = null,
    ): String {
        val id = randomUuid()
        val person = Person(
            id = id,
            name = name.trim(),
            intervalDays = intervalDays,
            sphereIds = sphereIds,
            city = city?.trim()?.ifEmpty { null },
            eventIds = eventIds,
            followUpAt = null,
            baseContactAt = baseContactAt,
            birthday = birthday,
            gifts = emptyList(),
            note = note,
            facts = emptyList(),
            snoozedUntil = null,
            dismissedAt = null,
            createdAt = currentTimeMillis(),
        )
        _state.update { s ->
            val updated = s.people + person
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_people", json.encodeToString(updated)) }
            s.copy(people = updated)
        }
        scope.launch {
            state.value.user?.let { backend.savePerson(it.uid, person, it.idToken) }
            syncReminderSchedule(state.value.people, state.value.entries)
        }
        return id
    }

    fun updatePerson(id: String, transform: (Person) -> Person) {
        val person = state.value.people.firstOrNull { it.id == id } ?: return
        val updated = transform(person)
        _state.update { s ->
            val list = s.people.map { if (it.id == id) updated else it }
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_people", json.encodeToString(list)) }
            s.copy(people = list)
        }
        scope.launch {
            state.value.user?.let { backend.savePerson(it.uid, updated, it.idToken) }
            syncReminderSchedule(state.value.people, state.value.entries)
        }
    }

    fun deletePerson(id: String) {
        val photoUrl = state.value.people.firstOrNull { it.id == id }?.photoUrl
        val personEntries = state.value.entries.filter { it.personId == id }
        personEntries.forEach { deleteEntry(it.id) }
        _state.update { s ->
            val updated = s.people.filter { it.id != id }
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_people", json.encodeToString(updated)) }
            s.copy(people = updated)
        }
        scope.launch {
            val token = state.value.user?.idToken
            state.value.user?.let { backend.deletePerson(it.uid, id, token) }
            if (!photoUrl.isNullOrBlank()) {
                AvatarPhotoCache.remove(photoUrl)
                backend.deleteAvatarPhotoByUrl(photoUrl, token)
            }
            syncReminderSchedule(state.value.people, state.value.entries)
        }
    }

    private suspend fun refreshSessionToken(): String? {
        val currentUser = state.value.user ?: return null
        val refresh = currentUser.refreshToken?.takeIf { it.isNotBlank() } ?: return currentUser.idToken
        val refreshed = backend.refreshAuthToken(refresh) ?: return currentUser.idToken
        val updatedUser = currentUser.copy(idToken = refreshed.first, refreshToken = refreshed.second)
        PlatformStorage.putString("auth_user", json.encodeToString(updatedUser))
        _state.update { it.copy(user = updatedUser) }
        return refreshed.first
    }

    suspend fun downloadPhotoBytes(url: String): ByteArray? {
        val token = state.value.user?.idToken
        return backend.downloadBytes(url, token)
    }

    suspend fun updatePersonAvatar(
        personId: String,
        avatar: AvatarConfig?,
        newPhotoBytes: ByteArray?,
        removePhoto: Boolean,
    ): Result<Unit> {
        val person = state.value.people.firstOrNull { it.id == personId }
            ?: return Result.failure(Exception("Connection not found"))
        val previousPhotoUrl = person.photoUrl

        val finalPhotoUrl = if (newPhotoBytes != null) {
            val user = state.value.user ?: return Result.failure(Exception("Sign in to sync photos"))
            val version = currentTimeMillis()
            var token = user.idToken
            var upload = backend.uploadAvatarPhoto(user.uid, personId, version, newPhotoBytes, token)
            if (upload.downloadUrl == null && (upload.statusCode == 401 || upload.statusCode == 403)) {
                token = refreshSessionToken()
                if (!token.isNullOrBlank()) {
                    upload = backend.uploadAvatarPhoto(user.uid, personId, version, newPhotoBytes, token)
                }
            }
            val uploadedUrl = upload.downloadUrl
                ?: return Result.failure(Exception("Couldn't upload photo"))
            AvatarPhotoCache.seed(uploadedUrl, newPhotoBytes)
            uploadedUrl
        } else if (removePhoto) {
            null
        } else {
            previousPhotoUrl
        }

        updatePerson(personId) {
            it.copy(
                avatar = avatar,
                photoUrl = finalPhotoUrl,
            )
        }

        if (!previousPhotoUrl.isNullOrBlank() && previousPhotoUrl != finalPhotoUrl) {
            AvatarPhotoCache.remove(previousPhotoUrl)
            scope.launch {
                val token = state.value.user?.idToken
                backend.deleteAvatarPhotoByUrl(previousPhotoUrl, token)
            }
        }
        return Result.success(Unit)
    }

    fun setMembership(personId: String, sphereId: String, member: Boolean) {
        val person = state.value.people.firstOrNull { it.id == personId } ?: return
        val rest = person.sphereIds.filter { it != sphereId }
        updatePerson(personId) { it.copy(sphereIds = if (member) rest + sphereId else rest) }
    }

    fun logEntry(
        personId: String,
        kind: EntryKind,
        text: String = "",
        at: Long? = null,
        quality: Quality? = null,
    ): String {
        val id = randomUuid()
        val t = currentTimeMillis()
        val entry = Entry(
            id = id,
            personId = personId,
            kind = kind,
            text = text,
            quality = if (kind.isNote) null else quality,
            at = at ?: t,
            createdAt = t,
        )
        _state.update { s ->
            val updated = s.entries + entry
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_entries", json.encodeToString(updated)) }
            s.copy(entries = updated)
        }
        scope.launch {
            state.value.user?.let { backend.saveEntry(it.uid, entry, it.idToken) }
            syncReminderSchedule(state.value.people, state.value.entries)
        }
        val person = state.value.people.firstOrNull { it.id == personId }
        if (!kind.isNote && person?.followUpAt != null && person.followUpAt <= t + DAY) {
            updatePerson(person.id) { it.copy(followUpAt = null) }
        }
        return id
    }

    fun updateEntry(id: String, transform: (Entry) -> Entry) {
        val entry = state.value.entries.firstOrNull { it.id == id } ?: return
        val updated = transform(entry)
        _state.update { s ->
            val list = s.entries.map { if (it.id == id) updated else it }
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_entries", json.encodeToString(list)) }
            s.copy(entries = list)
        }
        scope.launch {
            state.value.user?.let { backend.saveEntry(it.uid, updated, it.idToken) }
            syncReminderSchedule(state.value.people, state.value.entries)
        }
    }

    fun deleteEntry(id: String) {
        _state.update { s ->
            val updated = s.entries.filter { it.id != id }
            s.user?.let { u -> PlatformStorage.putString("cache_${u.uid}_entries", json.encodeToString(updated)) }
            s.copy(entries = updated)
        }
        scope.launch {
            state.value.user?.let { backend.deleteEntry(it.uid, id, it.idToken) }
            syncReminderSchedule(state.value.people, state.value.entries)
        }
    }

    fun quickCheckIn(personId: String, kind: InteractionKind = InteractionKind.CHECKIN) {
        val person = state.value.people.firstOrNull { it.id == personId }
        val prevSnooze = person?.snoozedUntil
        val prevFollowUp = person?.followUpAt
        val entryId = logEntry(personId = personId, kind = kind.toEntryKind())
        if (prevSnooze != null) {
            updatePerson(personId) { it.copy(snoozedUntil = null) }
        }
        val first = firstName(person?.name ?: "them")
        showToast("Checked in with $first") {
            deleteEntry(entryId)
            updatePerson(personId) { it.copy(snoozedUntil = prevSnooze, followUpAt = prevFollowUp) }
        }
    }

    fun snooze(personId: String, days: Int) {
        val person = state.value.people.firstOrNull { it.id == personId }
        val prev = person?.snoozedUntil
        updatePerson(personId) { it.copy(snoozedUntil = currentTimeMillis() + days * DAY) }
        val first = firstName(person?.name ?: "them")
        showToast("Snoozed $first for ${if (days == 1) "a day" else "$days days"}") {
            updatePerson(personId) { it.copy(snoozedUntil = prev) }
        }
    }

    fun dismissSuggestion(personId: String) {
        val person = state.value.people.firstOrNull { it.id == personId }
        updatePerson(personId) { it.copy(dismissedAt = currentTimeMillis()) }
        val first = firstName(person?.name ?: "them")
        showToast("Dismissed $first") {
            updatePerson(personId) { it.copy(dismissedAt = null) }
        }
    }

    fun keepInTouch(personId: String, intervalDays: Int) {
        updatePerson(personId) { it.copy(intervalDays = intervalDays, dismissedAt = null) }
    }

    fun setFollowUp(personId: String, days: Int?) {
        val person = state.value.people.firstOrNull { it.id == personId } ?: return
        val prev = person.followUpAt
        if (days == null) {
            updatePerson(personId) { it.copy(followUpAt = null) }
            return
        }
        val targetTime = at9(currentTimeMillis() + days * DAY)
        updatePerson(personId) { it.copy(followUpAt = targetTime) }
        val text = when (days) {
            7 -> "1 week"
            1 -> "1 day"
            else -> "$days days"
        }
        showToast("Reminder in $text") {
            updatePerson(personId) { it.copy(followUpAt = prev) }
        }
    }

    fun quickAddToEvent(eventId: String, raw: String): String? {
        val parsed = parseQuickAdd(raw)
        if (parsed.name.isEmpty()) return null
        val current = state.value
        val event = current.events.firstOrNull { it.id == eventId }
        if (event != null) {
            val limitStatus = evaluateEventLimitStatus(
                event = event,
                people = current.people,
                isUnlimited = current.isUnlimited,
                now = currentTimeMillis(),
            )
            if (limitStatus == EventLimitStatus.HARD_LIMIT) {
                val reason = if (wasEventCreatedBeforeLimit(event, current.people)) {
                    PaywallReason.EventGraceEnded(event.name)
                } else {
                    PaywallReason.ConnectionsLimit
                }
                openSheet(SheetState.Paywall(reason = reason))
                return null
            }
        } else if (!canAddPerson(current.people.size, current.isUnlimited)) {
            openSheet(SheetState.Paywall(reason = PaywallReason.ConnectionsLimit))
            return null
        }
        return addPerson(
            name = parsed.name,
            note = parsed.note,
            intervalDays = null,
            sphereIds = emptyList(),
            eventIds = listOf(eventId),
        )
    }

    fun exportData(): String {
        val s = state.value
        @Serializable
        data class ExportPayload(
            val exportedAt: String,
            val spheres: List<Sphere>,
            val events: List<SocialEvent>,
            val people: List<Person>,
            val entries: List<Entry>,
        )
        val payload = ExportPayload(
            exportedAt = Instant.fromEpochMilliseconds(currentTimeMillis()).toString(),
            spheres = s.spheres,
            events = s.events,
            people = s.people,
            entries = s.entries,
        )
        return Json { prettyPrint = true }.encodeToString(payload)
    }
}
