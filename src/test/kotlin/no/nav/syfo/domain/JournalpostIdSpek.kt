package no.nav.syfo.domain

import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class JournalpostIdSpek : Spek({
    describe(JournalpostId::class.java.simpleName) {
        it("creates JournalpostId from numeric value") {
            val journalpostId = JournalpostId("123")
            journalpostId.shouldNotBeNull()
        }
        it("throws exception when value is not numeric") {
            assertFailsWith(IllegalArgumentException::class) {
                JournalpostId("asb")
            }
        }
    }
})
