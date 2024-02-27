package no.nav.syfo.infrastructure.database

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateForhandsvarselVurdering
import org.amshove.kluent.internal.assertFailsWith
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.lang.IllegalStateException

class VurderingRepositorySpek : Spek({
    describe(VurderingRepository::class.java.simpleName) {

        val database = ExternalMockEnvironment.instance.database
        val vurderingRepository = VurderingRepository(database = database)

        afterEachTest {
            database.dropData()
        }

        describe("createForhandsvarsel") {
            val vurdering = generateForhandsvarselVurdering()

            it("creates vurdering, varsel and pdf in database") {
                vurderingRepository.createForhandsvarsel(
                    pdf = UserConstants.PDF_FORHANDSVARSEL,
                    vurdering = vurdering,
                )
                // TODO: Sjekk at ting er lagret når vi har implementert spørringer
            }

            it("fails if vurdering is missing a varsel") {
                val vurderingWithoutVarsel = generateForhandsvarselVurdering().copy(varsel = null)

                assertFailsWith(IllegalStateException::class) {
                    vurderingRepository.createForhandsvarsel(
                        pdf = UserConstants.PDF_FORHANDSVARSEL,
                        vurdering = vurderingWithoutVarsel,
                    )
                }
            }
        }
    }
})
