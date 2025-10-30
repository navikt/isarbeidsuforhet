package no.nav.syfo.domain

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

class JournalpostIdTest {
    @Test
    fun createsJournalpostIdFromNumericValue() {
        val journalpostId = JournalpostId("123")
        assertNotNull(journalpostId)
    }

    @Test
    fun throwsExceptionWhenValueIsNotNumeric() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalpostId("asb")
        }
    }
}
