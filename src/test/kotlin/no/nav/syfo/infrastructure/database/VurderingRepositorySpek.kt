package no.nav.syfo.infrastructure.database

import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateDocumentComponent
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class VurderingRepositorySpek : Spek({
    describe(VurderingRepository::class.java.simpleName) {

        val database = TestDatabase()
        val vurderingRepository = VurderingRepository(database = database)

        afterEachTest {
            database.dropData()
        }

        describe("createForhandsvarsel") {
            it("creates vurdering, varsel and pdf in database") {
                val begrunnelse = "Fin begrunnelse"
                vurderingRepository.createForhandsvarsel(
                    pdf = UserConstants.PDF_FORHANDSVARSEL,
                    document = generateDocumentComponent(fritekst = begrunnelse),
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    veileder = UserConstants.VEILEDER_IDENT,
                    type = "FORHANDSVARSEL",
                    begrunnelse = begrunnelse,
                )
                // TODO: Sjekk at ting er lagret når vi har implementert spørringer
            }
        }
    }
})
