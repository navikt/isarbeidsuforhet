package no.nav.syfo.application.service

import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.clients.pdfgen.VurderingPdfService
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVurdering
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class VurderingServiceSpek : Spek ({
    describe(VurderingService::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val vurderingRepository = VurderingRepository(database = database)
        val vurderingPdfService = VurderingPdfService(
            externalMockEnvironment.pdfgenClient,
            externalMockEnvironment.pdlClient,
        )
        val journalforingService = JournalforingService(
            dokarkivClient = externalMockEnvironment.dokarkivClient,
            pdlClient = externalMockEnvironment.pdlClient,
        )
        val vurderingService = VurderingService(
            vurderingRepository = vurderingRepository,
            vurderingPdfService = vurderingPdfService,
            journalforingService = journalforingService,

        )

        afterEachTest {
            database.dropData()
        }

        val vurdering = generateForhandsvarselVurdering()

        describe("Journalføring") {
            it("journalfører forhåndsvarsel") {
                vurderingRepository.createForhandsvarsel(
                    pdf = UserConstants.PDF_FORHANDSVARSEL,
                    vurdering = vurdering,
                )

                runBlocking {
                    val journalforteVurderinger = vurderingService.journalforVurderinger()

                    val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                    failed.size shouldBeEqualTo 0
                    success.size shouldBeEqualTo 1

                    val journalfortVurdering = success.first().getOrThrow()
                    journalfortVurdering.journalpostId shouldBeEqualTo "1"

                    val pVurdering = database.getVurdering(journalfortVurdering.uuid)
                    pVurdering!!.updatedAt shouldBeGreaterThan pVurdering.createdAt
                }
            }

            it("journalfører ikke når ingen forhåndsvarsler") {
                runBlocking {
                    val journalforteVurderinger = vurderingService.journalforVurderinger()

                    val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                    failed.size shouldBeEqualTo 0
                    success.size shouldBeEqualTo 0
                }
            }

            it("journalfører ikke når forhåndsvarsel allerede er journalført") {
                vurderingRepository.createForhandsvarsel(
                    pdf = UserConstants.PDF_FORHANDSVARSEL,
                    vurdering = vurdering,
                )
                val journalfortVarsel = vurdering.journalfor(journalpostId = "1")
                vurderingRepository.update(journalfortVarsel)

                runBlocking {
                    val journalforteVurderinger = vurderingService.journalforVurderinger()

                    val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                    failed.size shouldBeEqualTo 0
                    success.size shouldBeEqualTo 0
                }
            }

            it("journalfører forhåndsvarsel selv om noen feiler") {
                vurderingRepository.createForhandsvarsel(
                    pdf = UserConstants.PDF_FORHANDSVARSEL,
                    vurdering = vurdering,
                )

                val vurderingFails = generateForhandsvarselVurdering(
                    personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS,
                )
                vurderingRepository.createForhandsvarsel(
                    pdf = UserConstants.PDF_FORHANDSVARSEL,
                    vurdering = vurderingFails,
                )

                runBlocking {
                    val journalforteVurderinger = vurderingService.journalforVurderinger()

                    val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                    failed.size shouldBeEqualTo 1
                    success.size shouldBeEqualTo 1
                }
            }
        }
    }
})