package no.nav.syfo.domain

import no.nav.syfo.generator.generateForhandsvarselVurdering
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class ForhandsvarselSpek : Spek({
    describe("isExpiredForhandsvarsel") {
        it("return false when svarfrist is tomorrow") {
            val forhandsvarsel = generateForhandsvarselVurdering().copy(
                varsel = Varsel().copy(svarfrist = LocalDate.now().plusDays(1))
            )
            forhandsvarsel.isExpiredForhandsvarsel().shouldBeFalse()
        }

        it("return false when svarfrist is today") {
            val forhandsvarsel = generateForhandsvarselVurdering().copy(
                varsel = Varsel().copy(svarfrist = LocalDate.now())
            )
            forhandsvarsel.isExpiredForhandsvarsel().shouldBeFalse()
        }

        it("return true when svarfrist was yesterday") {
            val forhandsvarsel = generateForhandsvarselVurdering().copy(
                varsel = Varsel().copy(svarfrist = LocalDate.now().minusDays(1))
            )
            forhandsvarsel.isExpiredForhandsvarsel().shouldBeTrue()
        }
    }
})
