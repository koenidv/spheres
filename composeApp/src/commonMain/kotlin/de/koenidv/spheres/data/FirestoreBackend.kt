package de.koenidv.spheres.data

import de.koenidv.spheres.domain.AppUser
import de.koenidv.spheres.domain.Entry
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.SocialEvent
import de.koenidv.spheres.domain.Sphere
import de.koenidv.spheres.platform.launchGoogleAuthFlow
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

const val FIREBASE_API_KEY = "AIzaSyCjx-Ip33oL9ipYlbMNw6DSgeqLMz61QqY"
const val FIREBASE_PROJECT_ID = "friendly-spheres"
const val FIREBASE_APP_ID = "1:467518573582:web:d9b980ac43a2d801cd805f"
const val GOOGLE_OAUTH_CLIENT_ID = "467518573582-5o6j7rfmuob5uo8b0bmdlpqjekrl3qqn.apps.googleusercontent.com"
const val GOOGLE_OAUTH_CALLBACK_SCHEME = "com.googleusercontent.apps.467518573582-5o6j7rfmuob5uo8b0bmdlpqjekrl3qqn"
const val GOOGLE_IOS_CLIENT_ID = "467518573582-3gs9g4jn3gvd63vnsrko5utltbu5der2.apps.googleusercontent.com"
const val GOOGLE_IOS_CALLBACK_SCHEME = "com.googleusercontent.apps.467518573582-3gs9g4jn3gvd63vnsrko5utltbu5der2"

private const val FIRESTORE_BASE =
    "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/(default)/documents"
private const val IDENTITY_TOOLKIT_URL =
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$FIREBASE_API_KEY"
private const val TOKEN_REFRESH_URL =
    "https://securetoken.googleapis.com/v1/token?key=$FIREBASE_API_KEY"

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

    suspend fun signInWithGoogleIdToken(idToken: String): AppUser? {
        return try {
            val payload = buildJsonObject {
                put("postBody", "id_token=$idToken&providerId=google.com")
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
