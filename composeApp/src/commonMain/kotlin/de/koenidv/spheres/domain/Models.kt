package de.koenidv.spheres.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Sphere(
    val id: String,
    val name: String,
    val color: String,
    val note: String = "",
    val createdAt: Long,
    val kind: String? = null,
    val date: Long? = null,
)

@Serializable
data class SocialEvent(
    val id: String,
    val name: String,
    val date: Long,
    val sphereIds: List<String> = emptyList(),
    val color: String,
    val note: String = "",
    val createdAt: Long,
    val createdBeforeLimit: Boolean? = null,
)

@Serializable
data class Birthday(
    val month: Int,
    val day: Int,
    val year: Int? = null,
)

@Serializable
data class Gift(
    val id: String,
    val text: String,
    val done: Boolean = false,
)

@Serializable
data class Fact(
    val id: String,
    val label: String,
    val value: String,
)

@Serializable
enum class Quality(val value: String, val symbol: String, val label: String, val weight: Double) {
    @SerialName("bad") BAD("bad", "-", "Bad", 0.5),
    @SerialName("regular") REGULAR("regular", "+", "Regular", 1.0),
    @SerialName("good") GOOD("good", "++", "Good", 1.5),
    @SerialName("amazing") AMAZING("amazing", "+++", "Amazing", 2.0);

    companion object {
        fun fromValue(value: String?): Quality = when (value) {
            "bad" -> BAD
            "good" -> GOOD
            "amazing" -> AMAZING
            else -> REGULAR
        }
    }
}

@Serializable
enum class InteractionKind(val value: String) {
    @SerialName("checkin") CHECKIN("checkin"),
    @SerialName("meet") MEET("meet"),
    @SerialName("call") CALL("call"),
    @SerialName("message") MESSAGE("message"),
    @SerialName("other") OTHER("other");

    companion object {
        fun fromValue(value: String?): InteractionKind = when (value) {
            "meet" -> MEET
            "call" -> CALL
            "message" -> MESSAGE
            "other" -> OTHER
            else -> CHECKIN
        }
    }
}

@Serializable
enum class EntryKind(val value: String) {
    @SerialName("checkin") CHECKIN("checkin"),
    @SerialName("meet") MEET("meet"),
    @SerialName("call") CALL("call"),
    @SerialName("message") MESSAGE("message"),
    @SerialName("other") OTHER("other"),
    @SerialName("note") NOTE("note");

    val isNote: Boolean get() = this == NOTE

    companion object {
        fun fromValue(value: String?): EntryKind = when (value) {
            "meet" -> MEET
            "call" -> CALL
            "message" -> MESSAGE
            "other" -> OTHER
            "note" -> NOTE
            else -> CHECKIN
        }
    }
}

fun InteractionKind.toEntryKind(): EntryKind = when (this) {
    InteractionKind.CHECKIN -> EntryKind.CHECKIN
    InteractionKind.MEET -> EntryKind.MEET
    InteractionKind.CALL -> EntryKind.CALL
    InteractionKind.MESSAGE -> EntryKind.MESSAGE
    InteractionKind.OTHER -> EntryKind.OTHER
}

@Serializable
data class AvatarConfig(
    val skin: Int? = null,
    val hairStyle: String? = null,
    val hairColor: Int? = null,
    val glasses: String? = null,
)

@Serializable
data class Person(
    val id: String,
    val name: String,
    val intervalDays: Int? = null,
    val sphereIds: List<String> = emptyList(),
    val city: String? = null,
    /** Optional street and number; [city] stays the coarse location. */
    val address: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val eventIds: List<String> = emptyList(),
    val followUpAt: Long? = null,
    val baseContactAt: Long? = null,
    val birthday: Birthday? = null,
    val gifts: List<Gift> = emptyList(),
    val note: String = "",
    val facts: List<Fact> = emptyList(),
    val snoozedUntil: Long? = null,
    val dismissedAt: Long? = null,
    val avatar: AvatarConfig? = null,
    val photoUrl: String? = null,
    val contactName: String? = null,
    val contactPhoneHashes: List<String> = emptyList(),
    val createdAt: Long,
)

@Serializable
data class Entry(
    val id: String,
    val personId: String,
    val kind: EntryKind,
    val text: String = "",
    val quality: Quality? = null,
    val at: Long,
    val createdAt: Long,
)

@Serializable
data class AppUser(
    val uid: String,
    val name: String,
    val email: String? = null,
    val photoUrl: String? = null,
    val idToken: String? = null,
    val refreshToken: String? = null,
)

@Serializable
data class BillingEntitlement(
    val id: String = "unlimited",
    val unlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val productId: String? = null,
    val platform: String? = null,
    val purchaseToken: String? = null,
    val revokedAt: Long? = null,
)

val Person.hasCoordinates: Boolean get() = lat != null && lng != null

/** One-line place for lists: "Street 1, Berlin" or just "Berlin". */
fun Person.locationLabel(): String? {
    val parts = listOfNotNull(address?.trim()?.ifEmpty { null }, city?.trim()?.ifEmpty { null })
    return parts.joinToString(", ").ifEmpty { null }
}
