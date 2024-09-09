package no.nav.syfo.domain

import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.generator.generateVurdering
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class VurderingSpek : Spek({
    describe("isExpiredForhandsvarsel") {
        it("return false when forhandsvarsel with svarfrist tomorrow") {
            val forhandsvarsel = generateForhandsvarselVurdering().copy(
                varsel = Varsel().copy(svarfrist = LocalDate.now().plusDays(1))
            )
            forhandsvarsel.isExpiredForhandsvarsel().shouldBeFalse()
        }

        it("return false when forhandsvarsel with svarfrist today") {
            val forhandsvarsel = generateForhandsvarselVurdering().copy(
                varsel = Varsel().copy(svarfrist = LocalDate.now())
            )
            forhandsvarsel.isExpiredForhandsvarsel().shouldBeFalse()
        }

        it("return true when forhandsvarsel with svarfrist yesterday") {
            val forhandsvarsel = generateForhandsvarselVurdering().copy(
                varsel = Varsel().copy(svarfrist = LocalDate.now().minusDays(1))
            )
            forhandsvarsel.isExpiredForhandsvarsel().shouldBeTrue()
        }
        it("returns false when not forhandsvarsel") {
            val vurdering = generateVurdering(type = VurderingType.OPPFYLT)
            vurdering.isExpiredForhandsvarsel().shouldBeFalse()
        }
    }
})
