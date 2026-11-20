package de.koenidv.spheres.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

enum class PlaceKind { ADDRESS, CITY }

data class PlaceResult(
    val title: String,
    val subtitle: String,
    val city: String?,
    val address: String?,
    val country: String?,
    val lat: Double,
    val lng: Double,
    val kind: PlaceKind,
)

/** Place search on top of Photon (photon.komoot.io, OpenStreetMap data), built for search-as-you-type. */
object Geocoder {
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(query: String, limit: Int = 6): List<PlaceResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return try {
            val response = client.get("https://photon.komoot.io/api/") {
                parameter("q", q)
                parameter("limit", limit + 4)
                header("User-Agent", "Spheres/1.0")
            }
            if (!response.status.isSuccess()) return emptyList()
            val features = json.parseToJsonElement(response.bodyAsText())
                .jsonObject["features"] as? JsonArray ?: return emptyList()
            features.mapNotNull { parse(it as? JsonObject) }
                .distinctBy { it.title + "|" + it.subtitle }
                .take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parse(feature: JsonObject?): PlaceResult? {
        feature ?: return null
        val coords = (feature["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray ?: return null
        val lng = (coords.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return null
        val lat = (coords.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return null
        val p = feature["properties"] as? JsonObject ?: return null
        fun str(key: String) = (p[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }

        val type = str("type")
        val name = str("name")
        val street = str("street") ?: if (type == "street") name else null
        val number = str("housenumber")
        val country = str("country")
        val state = str("state")
        val postcode = str("postcode")
        val locality = str("city") ?: str("district") ?: str("county")

        val address = street?.let { if (number != null) "$it $number" else it }
        return if (address != null) {
            val city = locality ?: state
            PlaceResult(
                title = if (type == "house" || type == "street") address else (name ?: address),
                subtitle = listOfNotNull(
                    listOfNotNull(postcode, city).joinToString(" ").ifEmpty { null },
                    country
                ).joinToString(", "),
                city = city,
                address = address,
                country = country,
                lat = lat,
                lng = lng,
                kind = PlaceKind.ADDRESS,
            )
        } else {
            val city = when (type) {
                "city", "locality", "district", "state", "county", "country", "other" -> name
                else -> locality ?: name
            } ?: return null
            PlaceResult(
                title = city,
                subtitle = listOfNotNull(state?.takeIf { it != city }, country?.takeIf { it != city })
                    .joinToString(", "),
                city = city,
                address = null,
                country = country,
                lat = lat,
                lng = lng,
                kind = PlaceKind.CITY,
            )
        }
    }
}
