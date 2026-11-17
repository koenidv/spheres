package de.koenidv.spheres.data

import de.koenidv.spheres.domain.AppUser
import de.koenidv.spheres.domain.BillingEntitlement
import de.koenidv.spheres.domain.Entry
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.SocialEvent
import de.koenidv.spheres.domain.Sphere
import de.koenidv.spheres.platform.launchGoogleAuthFlow
import de.koenidv.spheres.platform.signInWithAppleNative
import de.koenidv.spheres.platform.signInWithGoogleNative
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val FIREBASE_API_KEY = "AIzaSyCBUEx76YXxEpC-qtHNamly7vLR3RiB4V8"
const val FIREBASE_PROJECT_ID = "friendly-spheres"
const val FIREBASE_APP_ID = "1:467518573582:android:bde685cc9fa07ee6cd805f"
const val FIREBASE_STORAGE_BUCKET = "friendly-spheres.firebasestorage.app"
const val GOOGLE_OAUTH_CLIENT_ID = "467518573582-5o6j7rfmuob5uo8b0bmdlpqjekrl3qqn.apps.googleusercontent.com"
const val GOOGLE_OAUTH_CALLBACK_SCHEME = "com.googleusercontent.apps.467518573582-5o6j7rfmuob5uo8b0bmdlpqjekrl3qqn"
const val GOOGLE_IOS_CLIENT_ID = "467518573582-3j8mon1pe7fs6l75deic2f4pc8tncvhc.apps.googleusercontent.com"
const val GOOGLE_IOS_CALLBACK_SCHEME = "com.googleusercontent.apps.467518573582-3j8mon1pe7fs6l75deic2f4pc8tncvhc"

private const val FIRESTORE_BASE =
    "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/(default)/documents"
private const val STORAGE_BASE =
    "https://firebasestorage.googleapis.com/v0/b/$FIREBASE_STORAGE_BUCKET/o"
private const val IDENTITY_TOOLKIT_URL =
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$FIREBASE_API_KEY"
private const val TOKEN_REFRESH_URL =
    "https://securetoken.googleapis.com/v1/token?key=$FIREBASE_API_KEY"
private const val ACCOUNT_UPDATE_URL =
    "https://identitytoolkit.googleapis.com/v1/accounts:update?key=$FIREBASE_API_KEY"
private const val VERIFY_PURCHASE_URL =
    "https://us-central1-$FIREBASE_PROJECT_ID.cloudfunctions.net/verifyPurchase"
private const val DELETE_ACCOUNT_URL =
    "https://us-central1-$FIREBASE_PROJECT_ID.cloudfunctions.net/deleteAccount"
private const val REVOKE_TOKEN_URL =
    "https://identitytoolkit.googleapis.com/v2/accounts:revokeToken?key=$FIREBASE_API_KEY"

sealed interface BillingClaimResult {
    data object Claimed : BillingClaimResult
    data object Transferred : BillingClaimResult
    data object OwnedByOther : BillingClaimResult
    data class Error(val message: String) : BillingClaimResult
}

data class StorageUploadResult(val statusCode: Int, val downloadUrl: String?)

class FirestoreBackend(
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun signInWithGoogle(): AppUser {
        val nativeIdToken = signInWithGoogleNative(GOOGLE_OAUTH_CLIENT_ID)
        if (!nativeIdToken.isNullOrBlank()) {
            return signInWithGoogleIdToken(nativeIdToken)
                ?: throw Exception("Google sign-in failed")
        }

        val redirectUri = "$GOOGLE_IOS_CALLBACK_SCHEME:/oauth2redirect"
        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=$GOOGLE_IOS_CLIENT_ID&redirect_uri=$redirectUri&response_type=code&scope=openid%20email%20profile&prompt=select_account"
        val callbackUrl = launchGoogleAuthFlow(authUrl, GOOGLE_IOS_CALLBACK_SCHEME)
        val code = extractParam(callbackUrl, "code") ?: throw Exception("Missing authorization code")

        val tokenResponse = client.post("https://oauth2.googleapis.com/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("code=${code.encodeURLParameter()}&client_id=${GOOGLE_IOS_CLIENT_ID.encodeURLParameter()}&redirect_uri=${redirectUri.encodeURLParameter()}&grant_type=authorization_code")
        }
        val tokenBodyText = tokenResponse.bodyAsText()
        if (!tokenResponse.status.isSuccess()) {
            throw Exception("Failed to exchange auth code for tokens (${tokenResponse.status.value}): $tokenBodyText")
        }
        val tokenJson = json.parseToJsonElement(tokenBodyText) as? JsonObject
            ?: throw Exception("Invalid token response")
        val idToken = (tokenJson["id_token"] as? JsonPrimitive)?.content
            ?: throw Exception("Missing id_token in token response")

        return signInWithGoogleIdToken(idToken)
            ?: throw Exception("Firebase sign-in failed")
    }

    private fun extractParam(url: String, key: String): String? {
        val queryAndFrag = url.substringAfter("?", "") + "&" + url.substringAfter("#", "")
        for (part in queryAndFrag.split("&")) {
            val idx = part.indexOf("=")
            if (idx > 0) {
                val k = part.substring(0, idx)
                if (k == key) {
                    return part.substring(idx + 1).decodeURLQueryComponent()
                }
            }
        }
        return null
    }

    suspend fun signInWithApple(): AppUser {
        val credential = signInWithAppleNative()
        val user = signInWithIdp(
            "id_token=${credential.idToken}&providerId=apple.com&nonce=${credential.rawNonce.encodeURLParameter()}",
            credential.idToken
        ) ?: throw Exception("Apple sign-in failed")

        // Apple only shares the name on the first sign-in and Firebase doesn't store it, so persist it ourselves
        val fullName = credential.fullName?.takeIf { it.isNotBlank() } ?: return user
        val hasDisplayName = user.name != (user.email ?: "You")
        if (hasDisplayName) return user
        updateDisplayName(user.idToken, fullName)
        return user.copy(name = fullName)
    }

    private suspend fun updateDisplayName(idToken: String?, displayName: String) {
        if (idToken.isNullOrBlank()) return
        try {
            client.post(ACCOUNT_UPDATE_URL) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("idToken", idToken)
                    put("displayName", displayName)
                    put("returnSecureToken", false)
                })
            }
        } catch (_: Exception) {
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AppUser? =
        signInWithIdp("id_token=$idToken&providerId=google.com", idToken)

    private suspend fun signInWithIdp(postBody: String, idToken: String): AppUser? {
        return try {
            val payload = buildJsonObject {
                put("postBody", postBody)
                put("requestUri", "http://localhost")
                put("returnIdpCredential", true)
                put("returnSecureToken", true)
            }
            val response = client.post(IDENTITY_TOOLKIT_URL) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (!response.status.isSuccess()) return null
            val respJson = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return null
            val localId = (respJson["localId"] as? JsonPrimitive)?.content ?: return null
            val displayName = (respJson["displayName"] as? JsonPrimitive)?.content
            val email = (respJson["email"] as? JsonPrimitive)?.content
            val photoUrl = (respJson["photoUrl"] as? JsonPrimitive)?.content
            val newIdToken = (respJson["idToken"] as? JsonPrimitive)?.content ?: idToken
            val refreshToken = (respJson["refreshToken"] as? JsonPrimitive)?.content

            AppUser(
                uid = localId,
                name = displayName?.ifEmpty { null } ?: email?.ifEmpty { null } ?: "You",
                email = email,
                photoUrl = photoUrl,
                idToken = newIdToken,
                refreshToken = refreshToken,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Reads the `firebase.sign_in_provider` claim, e.g. "google.com" or "apple.com". */
    @OptIn(ExperimentalEncodingApi::class)
    fun signInProvider(idToken: String): String? = try {
        val payload = idToken.split(".")[1]
        val decoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload)
        val claims = json.parseToJsonElement(decoded.decodeToString()) as? JsonObject
        val firebase = claims?.get("firebase") as? JsonObject
        (firebase?.get("sign_in_provider") as? JsonPrimitive)?.content
    } catch (_: Exception) {
        null
    }

    /** Revokes the user's Apple tokens, as required by Apple when deleting an account. */
    suspend fun revokeAppleToken(idToken: String, authorizationCode: String): Boolean {
        return try {
            val response = client.post(REVOKE_TOKEN_URL) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("providerId", "apple.com")
                    put("tokenType", "CODE")
                    put("token", authorizationCode)
                    put("idToken", idToken)
                })
            }
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteAccount(idToken: String) {
        val response = client.post(DELETE_ACCOUNT_URL) {
            header("Authorization", "Bearer $idToken")
        }
        if (!response.status.isSuccess()) {
            throw Exception("Could not delete account (${response.status.value})")
        }
    }

    suspend fun refreshAuthToken(refreshToken: String): Pair<String, String>? {
        return try {
            val response = client.post(TOKEN_REFRESH_URL) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("grant_type=refresh_token&refresh_token=$refreshToken")
            }
            if (!response.status.isSuccess()) return null
            val respJson = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return null
            val idToken = (respJson["id_token"] as? JsonPrimitive)?.content ?: return null
            val newRefreshToken = (respJson["refresh_token"] as? JsonPrimitive)?.content ?: refreshToken
            Pair(idToken, newRefreshToken)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchSpheres(uid: String, idToken: String?): List<Sphere> =
        fetchCollection(uid, "spheres", idToken)

    suspend fun fetchEvents(uid: String, idToken: String?): List<SocialEvent> =
        fetchCollection(uid, "events", idToken)

    suspend fun fetchPeople(uid: String, idToken: String?): List<Person> =
        fetchCollection(uid, "people", idToken)

    suspend fun fetchEntries(uid: String, idToken: String?): List<Entry> =
        fetchCollection(uid, "entries", idToken)

    suspend fun saveSphere(uid: String, sphere: Sphere, idToken: String?) {
        saveDocument(uid, "spheres", sphere.id, sphere, idToken)
    }

    suspend fun deleteSphere(uid: String, id: String, idToken: String?) {
        deleteDocument(uid, "spheres", id, idToken)
    }

    suspend fun saveEvent(uid: String, event: SocialEvent, idToken: String?) {
        saveDocument(uid, "events", event.id, event, idToken)
    }

    suspend fun deleteEvent(uid: String, id: String, idToken: String?) {
        deleteDocument(uid, "events", id, idToken)
    }

    suspend fun savePerson(uid: String, person: Person, idToken: String?) {
        saveDocument(uid, "people", person.id, person, idToken)
    }

    suspend fun deletePerson(uid: String, id: String, idToken: String?) {
        deleteDocument(uid, "people", id, idToken)
    }

    suspend fun saveEntry(uid: String, entry: Entry, idToken: String?) {
        saveDocument(uid, "entries", entry.id, entry, idToken)
    }

    suspend fun deleteEntry(uid: String, id: String, idToken: String?) {
        deleteDocument(uid, "entries", id, idToken)
    }

    suspend fun fetchEntitlement(uid: String, idToken: String?): BillingEntitlement? =
        fetchCollection<BillingEntitlement>(uid, "billing", idToken)
            .firstOrNull { it.id == "unlimited" || it.unlocked }

    suspend fun saveEntitlement(uid: String, entitlement: BillingEntitlement, idToken: String?) {
        saveDocument(uid, "billing", entitlement.id, entitlement, idToken)
    }

    suspend fun uploadAvatarPhoto(
        uid: String,
        personId: String,
        version: Long,
        jpegBytes: ByteArray,
        idToken: String?,
    ): StorageUploadResult {
        return try {
            val objectPath = "users/$uid/avatars/${personId}_$version.jpg"
            val encodedName = objectPath.encodeURLParameter()
            val url = "$STORAGE_BASE?uploadType=media&name=$encodedName"
            val response = client.post(url) {
                contentType(ContentType.Image.JPEG)
                if (!idToken.isNullOrBlank()) {
                    header("Authorization", "Firebase $idToken")
                }
                setBody(jpegBytes)
            }
            val code = response.status.value
            if (!response.status.isSuccess()) {
                return StorageUploadResult(code, null)
            }
            val respJson = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            val downloadTokens = (respJson?.get("downloadTokens") as? JsonPrimitive)?.content
            val tokenParam = downloadTokens
                ?.split(",")
                ?.firstOrNull { it.isNotBlank() }
                ?.let { "&token=${it.encodeURLParameter()}" }
                .orEmpty()
            StorageUploadResult(code, "$STORAGE_BASE/$encodedName?alt=media$tokenParam")
        } catch (_: Exception) {
            StorageUploadResult(0, null)
        }
    }

    suspend fun downloadBytes(url: String, idToken: String? = null): ByteArray? {
        return try {
            val response = client.get(url) {
                if (!idToken.isNullOrBlank() && url.startsWith(STORAGE_BASE)) {
                    header("Authorization", "Firebase $idToken")
                }
            }
            if (!response.status.isSuccess()) return null
            response.readRawBytes()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun claimPurchase(
        idToken: String,
        platform: String,
        productId: String,
        purchaseToken: String,
        allowTransfer: Boolean = false,
    ): BillingClaimResult {
        return try {
            val payload = buildJsonObject {
                put("platform", platform)
                put("productId", productId)
                put("purchaseToken", purchaseToken)
                put("allowTransfer", allowTransfer)
            }
            val response = client.post(VERIFY_PURCHASE_URL) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $idToken")
                setBody(payload)
            }
            val text = response.bodyAsText()
            val bodyJson = try { json.parseToJsonElement(text) as? JsonObject } catch (_: Exception) { null }
            val status = (bodyJson?.get("status") as? JsonPrimitive)?.content
            when {
                response.status.value == 409 || status == "owned_by_other" -> BillingClaimResult.OwnedByOther
                response.status.isSuccess() && status == "transferred" -> BillingClaimResult.Transferred
                response.status.isSuccess() -> BillingClaimResult.Claimed
                else -> {
                    val err = (bodyJson?.get("error") as? JsonPrimitive)?.content
                        ?: "Could not verify purchase (${response.status.value})"
                    BillingClaimResult.Error(err)
                }
            }
        } catch (e: Exception) {
            BillingClaimResult.Error(e.message ?: "Network error while verifying purchase")
        }
    }

    suspend fun deleteAvatarPhotoByUrl(photoUrl: String, idToken: String?) {
        if (!photoUrl.startsWith("$STORAGE_BASE/")) return
        val rawPath = photoUrl.removePrefix("$STORAGE_BASE/").substringBefore("?")
        if (rawPath.isBlank()) return
        val objectPath = try {
            rawPath.decodeURLQueryComponent()
        } catch (_: Exception) {
            return
        }
        try {
            client.delete("$STORAGE_BASE/${objectPath.encodeURLParameter()}") {
                if (!idToken.isNullOrBlank()) {
                    header("Authorization", "Firebase $idToken")
                }
            }
        } catch (_: Exception) {
        }
    }

    private suspend inline fun <reified T> fetchCollection(
        uid: String,
        collection: String,
        idToken: String?
    ): List<T> {
        return try {
            val url = "$FIRESTORE_BASE/users/$uid/$collection?pageSize=300"
            val response = client.get(url) {
                if (!idToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $idToken")
                }
            }
            if (!response.status.isSuccess()) return emptyList()
            val text = response.bodyAsText()
            val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
            val documents = root["documents"] as? JsonArray ?: return emptyList()
            documents.mapNotNull { doc ->
                if (doc is JsonObject) decodeFirestoreDoc<T>(doc) else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend inline fun <reified T> saveDocument(
        uid: String,
        collection: String,
        id: String,
        item: T,
        idToken: String?
    ) {
        try {
            val url = "$FIRESTORE_BASE/users/$uid/$collection/$id"
            val fields = encodeFirestoreFields(item)
            val payload = buildJsonObject {
                put("fields", fields)
            }
            client.patch(url) {
                contentType(ContentType.Application.Json)
                if (!idToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $idToken")
                }
                setBody(payload)
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun deleteDocument(
        uid: String,
        collection: String,
        id: String,
        idToken: String?
    ) {
        try {
            val url = "$FIRESTORE_BASE/users/$uid/$collection/$id"
            client.delete(url) {
                if (!idToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $idToken")
                }
            }
        } catch (_: Exception) {
        }
    }

    fun jsonElementToFirestoreValue(element: JsonElement): JsonObject {
        return when (element) {
            is JsonPrimitive -> {
                if (element is JsonNull) {
                    buildJsonObject { put("nullValue", JsonNull) }
                } else if (element.isString) {
                    buildJsonObject { put("stringValue", element.content) }
                } else {
                    val bool = element.booleanOrNull
                    if (bool != null) {
                        buildJsonObject { put("booleanValue", bool) }
                    } else {
                        val longVal = element.longOrNull
                        if (longVal != null) {
                            buildJsonObject { put("integerValue", longVal.toString()) }
                        } else {
                            val doubleVal = element.doubleOrNull
                            if (doubleVal != null) {
                                buildJsonObject { put("doubleValue", doubleVal) }
                            } else {
                                buildJsonObject { put("stringValue", element.content) }
                            }
                        }
                    }
                }
            }
            is JsonArray -> {
                buildJsonObject {
                    putJsonObject("arrayValue") {
                        putJsonArray("values") {
                            element.forEach { add(jsonElementToFirestoreValue(it)) }
                        }
                    }
                }
            }
            is JsonObject -> {
                buildJsonObject {
                    putJsonObject("mapValue") {
                        putJsonObject("fields") {
                            element.forEach { (k, v) -> put(k, jsonElementToFirestoreValue(v)) }
                        }
                    }
                }
            }
        }
    }

    fun firestoreValueToJsonElement(obj: JsonObject): JsonElement {
        if (obj.containsKey("nullValue")) return JsonNull
        obj["stringValue"]?.let { return it }
        obj["booleanValue"]?.let { return it }
        obj["integerValue"]?.let {
            val longVal = (it as? JsonPrimitive)?.content?.toLongOrNull()
            return if (longVal != null) JsonPrimitive(longVal) else it
        }
        obj["doubleValue"]?.let { return it }
        obj["mapValue"]?.let {
            val fields = (it as? JsonObject)?.get("fields") as? JsonObject
            return if (fields != null) {
                buildJsonObject {
                    fields.forEach { (k, v) ->
                        if (v is JsonObject) put(k, firestoreValueToJsonElement(v))
                    }
                }
            } else JsonNull
        }
        obj["arrayValue"]?.let {
            val values = (it as? JsonObject)?.get("values") as? JsonArray
            return if (values != null) {
                buildJsonArray {
                    values.forEach { elem ->
                        if (elem is JsonObject) add(firestoreValueToJsonElement(elem))
                    }
                }
            } else JsonArray(emptyList())
        }
        return JsonNull
    }

    inline fun <reified T> decodeFirestoreDoc(docJson: JsonObject): T? {
        val name = (docJson["name"] as? JsonPrimitive)?.content ?: ""
        val docId = name.substringAfterLast("/")
        val fields = docJson["fields"] as? JsonObject ?: return null
        val rootMap = buildJsonObject {
            put("id", docId)
            fields.forEach { (k, v) ->
                if (v is JsonObject) {
                    put(k, firestoreValueToJsonElement(v))
                }
            }
        }
        return try {
            json.decodeFromJsonElement<T>(rootMap)
        } catch (_: Exception) {
            null
        }
    }

    inline fun <reified T> encodeFirestoreFields(item: T): JsonObject {
        val jsonElement = json.encodeToJsonElement(item) as JsonObject
        return buildJsonObject {
            jsonElement.forEach { (k, v) ->
                if (k != "id") {
                    put(k, jsonElementToFirestoreValue(v))
                }
            }
        }
    }
}
