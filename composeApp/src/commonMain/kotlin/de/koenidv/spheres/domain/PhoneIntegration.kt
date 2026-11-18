package de.koenidv.spheres.domain

import de.koenidv.spheres.platform.PhoneCallRecord
import de.koenidv.spheres.platform.sha256Hex

const val MIN_PHONE_CALL_DURATION_SECONDS = 600L
const val UNLINKED_CALL_NOTIFY_DURATION_SECONDS = 1800L

private val PHONE_FACT_LABELS = setOf("phone", "mobile", "tel", "telephone")

fun normalizePhoneDigits(raw: String): String = raw.filter { it.isDigit() }

fun canonicalPhoneKey(raw: String): String? {
    val digits = normalizePhoneDigits(raw).trimStart('0')
    if (digits.length < 5) return null
    return if (digits.length > 9) digits.takeLast(9) else digits
}

fun hashPhoneNumber(
    raw: String,
    salt: String,
    hasher: (String) -> String = ::sha256Hex,
): String? {
    val key = canonicalPhoneKey(raw) ?: return null
    return hasher("$salt:$key")
}

fun hashPhoneNumbers(
    numbers: List<String>,
    salt: String,
    hasher: (String) -> String = ::sha256Hex,
): List<String> = numbers.mapNotNull { hashPhoneNumber(it, salt, hasher) }.distinct()

fun phoneNumbersMatch(a: String, b: String): Boolean {
    val da = normalizePhoneDigits(a).trimStart('0')
    val db = normalizePhoneDigits(b).trimStart('0')
    if (da.length < 5 || db.length < 5) return false
    if (da == db) return true
    if (da.length >= 7 && db.length >= 7) {
        return da.endsWith(db) || db.endsWith(da)
    }
    return false
}

fun parseContactBirthday(raw: String?): Birthday? {
    val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val noYearMatch = Regex("^--(\\d{2})-(\\d{2})$").matchEntire(s)
    if (noYearMatch != null) {
        val month = noYearMatch.groupValues[1].toIntOrNull() ?: return null
        val day = noYearMatch.groupValues[2].toIntOrNull() ?: return null
        if (month in 1..12 && day in 1..31) return Birthday(month = month, day = day, year = null)
        return null
    }
    val fullMatch = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:T.*)?$").matchEntire(s)
    if (fullMatch != null) {
        val year = fullMatch.groupValues[1].toIntOrNull()
        val month = fullMatch.groupValues[2].toIntOrNull() ?: return null
        val day = fullMatch.groupValues[3].toIntOrNull() ?: return null
        if (month in 1..12 && day in 1..31) {
            val validYear = if (year != null && year in 1901..2100) year else null
            return Birthday(month = month, day = day, year = validYear)
        }
    }
    return null
}

fun matchPersonForCall(
    call: PhoneCallRecord,
    people: List<Person>,
    localContactIds: Map<String, String> = emptyMap(),
    salt: String? = null,
    hasher: (String) -> String = ::sha256Hex,
): Person? {
    val contactId = call.contactId?.takeIf { it.isNotBlank() }
    if (contactId != null) {
        val linked = people.firstOrNull { localContactIds[it.id] == contactId }
        if (linked != null) return linked
    }

    val number = call.number?.takeIf { it.isNotBlank() }
    if (number != null) {
        if (!salt.isNullOrBlank()) {
            val hash = hashPhoneNumber(number, salt, hasher)
            if (hash != null) {
                val hashMatches = people.filter { hash in it.contactPhoneHashes }
                if (hashMatches.size == 1) return hashMatches.first()
            }
        }

        val numberMatches = people.filter { person ->
            person.facts.any { fact ->
                fact.label.trim().lowercase() in PHONE_FACT_LABELS &&
                    phoneNumbersMatch(fact.value, number)
            }
        }
        if (numberMatches.size == 1) return numberMatches.first()
    }

    return null
}
