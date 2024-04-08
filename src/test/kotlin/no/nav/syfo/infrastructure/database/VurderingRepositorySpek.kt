package no.nav.syfo.infrastructure.database

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.PDF_VURDERING
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.generator.generateVurdering
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class VurderingRepositorySpek : Spek({
    describe(VurderingRepository::class.java.simpleName) {

        val database = ExternalMockEnvironment.instance.database
        val vurderingRepository = VurderingRepository(database = database)

        afterEachTest {
            database.dropData()
        }

        describe("createForhandsvarsel") {
            val vurderingForhandsvarsel = generateForhandsvarselVurdering()

            it("creates vurdering, varsel and pdf in database") {
                vurderingRepository.createVurdering(
                    pdf = PDF_FORHANDSVARSEL,
                    vurdering = vurderingForhandsvarsel,
                )

                val vurdering = vurderingRepository.getVurderinger(vurderingForhandsvarsel.personident).firstOrNull()
                vurdering?.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                vurdering?.journalpostId shouldBeEqualTo null
                vurdering?.varsel?.uuid shouldBeEqualTo vurderingForhandsvarsel.varsel.uuid

                val pdf = database.getVurderingPdf(vurdering!!.uuid)?.pdf
                pdf shouldNotBeEqualTo null
                pdf?.get(0) shouldBeEqualTo PDF_FORHANDSVARSEL[0]
                pdf?.get(1) shouldBeEqualTo PDF_FORHANDSVARSEL[1]
            }
        }

        describe("Create vurdering") {
            val vurderingOppfylt = generateVurdering(type = VurderingType.OPPFYLT)

            it("Creates vurdering OPPFYLT and pdf without varsel") {
                vurderingRepository.createVurdering(
                    vurdering = vurderingOppfylt,
                    pdf = PDF_VURDERING,
                )

                val vurdering = vurderingRepository.getVurderinger(vurderingOppfylt.personident).firstOrNull()
                vurdering?.type shouldBeEqualTo VurderingType.OPPFYLT
                vurdering?.journalpostId shouldBeEqualTo null
                vurdering?.varsel shouldBeEqualTo null

                val pdf = database.getVurderingPdf(vurdering!!.uuid)?.pdf
                pdf shouldNotBeEqualTo null
                pdf?.get(0) shouldBeEqualTo PDF_VURDERING[0]
                pdf?.get(1) shouldBeEqualTo PDF_VURDERING[1]
            }

            it("Creates vurdering AVSLAG and pdf without varsel") {
                val vurderingAvslag = generateVurdering(type = VurderingType.AVSLAG)
                vurderingRepository.createVurdering(
                    vurdering = vurderingAvslag,
                    pdf = PDF_VURDERING,
                )

                val vurdering = vurderingRepository.getVurderinger(vurderingAvslag.personident).firstOrNull()
                vurdering?.type shouldBeEqualTo VurderingType.AVSLAG
                vurdering?.journalpostId shouldBeEqualTo null
                vurdering?.varsel shouldBeEqualTo null

                val pdf = database.getVurderingPdf(vurdering!!.uuid)?.pdf
                pdf shouldNotBeEqualTo null
                pdf?.get(0) shouldBeEqualTo PDF_VURDERING[0]
                pdf?.get(1) shouldBeEqualTo PDF_VURDERING[1]
            }
        }
    }
})
