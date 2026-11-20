package de.koenidv.spheres.domain

import de.koenidv.spheres.platform.PhoneCallRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneIntegrationTest {

    private fun testHasher(input: String): String = "h:${input.hashCode()}"

    private fun person(
        id: String,
        name: String,
        contactPhoneHashes: List<String> = emptyList(),
        facts: List<Fact> = emptyList(),
    ) = Person(
        id = id,
        name = name,
        contactPhoneHashes = contactPhoneHashes,
        facts = facts,
        createdAt = 1000L,
    )

    @Test
    fun phoneNumbersMatchHandlesFormattingAndCountryCodes() {
        assertTrue(phoneNumbersMatch("+49 170 1234567", "01701234567"))
        assertTrue(phoneNumbersMatch("(555) 123-4567", "5551234567"))
        assertFalse(phoneNumbersMatch("123", "123"))
        assertFalse(phoneNumbersMatch("+49 170 1234567", "+49 170 7654321"))
    }

    @Test
    fun canonicalPhoneKeyNormalizesInternationalAndLocalNumbersToSameKey() {
        assertEquals(canonicalPhoneKey("+49 170 1234567"), canonicalPhoneKey("0170 1234567"))
        assertEquals(canonicalPhoneKey("+1 (555) 123-4567"), canonicalPhoneKey("555-123-4567"))
        assertNull(canonicalPhoneKey("1234"))
    }

    @Test
    fun parseContactBirthdayHandlesWithAndWithoutYear() {
        assertEquals(Birthday(month = 5, day = 14, year = 1995), parseContactBirthday("1995-05-14"))
        assertEquals(Birthday(month = 11, day = 3, year = null), parseContactBirthday("--11-03"))
        assertNull(parseContactBirthday(""))
        assertNull(parseContactBirthday("invalid"))
    }

    @Test
    fun matchPersonForCallMatchesByLocalContactIdFirst() {
        val alice = person(id = "p1", name = "Alice")
        val bob = person(
            id = "p2",
            name = "Bob Smith",
            facts = listOf(Fact(id = "f1", label = "Phone", value = "01701234567")),
        )
        val call = PhoneCallRecord(
            id = "c1",
            number = "01701234567",
            contactId = "lookup-alice",
            contactName = "Alice",
            startedAt = 5000L,
            durationSeconds = 700L,
        )
        assertEquals(
            alice,
            matchPersonForCall(
                call = call,
                people = listOf(alice, bob),
                localContactIds = mapOf("p1" to "lookup-alice"),
            )
        )
    }

    @Test
    fun matchPersonForCallMatchesAcrossDevicesViaSaltedPhoneHash() {
        val uid = "user-123"
        val hashes = hashPhoneNumbers(listOf("+49 170 1234567"), salt = uid, hasher = ::testHasher)
        val alice = person(id = "p1", name = "Alice", contactPhoneHashes = hashes)
        val call = PhoneCallRecord(
            id = "c2",
            number = "0170 1234567",
            contactId = "new-device-lookup-id",
            contactName = "Alice",
            startedAt = 5000L,
            durationSeconds = 700L,
        )
        assertEquals(
            alice,
            matchPersonForCall(
                call = call,
                people = listOf(alice),
                localContactIds = emptyMap(),
                salt = uid,
                hasher = ::testHasher,
            )
        )
    }

    @Test
    fun matchPersonForCallDoesNotAutoMatchUnlinkedContactByNameAlone() {
        val alex = person(id = "p1", name = "Alex")
        val call = PhoneCallRecord(
            id = "c3",
            number = "+49 170 9999999",
            contactId = "lookup-alex",
            contactName = "Alex",
            startedAt = 5000L,
            durationSeconds = 1900L,
        )
        assertNull(matchPersonForCall(call, listOf(alex)))
    }

    @Test
    fun matchPersonForCallFallsBackToUniqueContactName() {
        val anna = person("a", "Anna").copy(contactName = "Anna M.")
        val call = PhoneCallRecord(id = "1", contactName = "anna m.", startedAt = 1L, durationSeconds = 700L)
        assertEquals("a", matchPersonForCall(call, listOf(anna, person("b", "Ben")))?.id)
        assertNull(matchPersonForCall(call, listOf(anna, person("c", "Anna").copy(contactName = "Anna M."))))
    }
}
