package no.nav.syfo.infrastructure.journalforing

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.PDF_VURDERING
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.generator.generateJournalpostRequest
import no.nav.syfo.generator.generateVurdering
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.BrevkodeType
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.JournalpostType
import no.nav.syfo.infrastructure.mock.dokarkivResponse
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object JournalforingServiceSpek : Spek({
    describe(JournalforingService::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val dokarkivMock = mockk<DokarkivClient>(relaxed = true)
        val journalforingService = JournalforingService(
            dokarkivClient = dokarkivMock,
            pdlClient = externalMockEnvironment.pdlClient,
        )

        val vurderingOppfylt = generateVurdering(type = VurderingType.OPPFYLT)
        val vurderingAvslag = generateVurdering(type = VurderingType.AVSLAG)
        val vurderingForhandsvarsel = generateForhandsvarselVurdering()

        beforeEachTest {
            clearAllMocks()
            coEvery { dokarkivMock.journalfor(any()) } returns dokarkivResponse
        }

        describe("Journalføring") {
            it("Journalfører OPPFYLT vurdering") {
                val journalpostId = runBlocking {
                    journalforingService.journalfor(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        pdf = PDF_VURDERING,
                        vurdering = vurderingOppfylt,
                    )
                }

                journalpostId shouldBeEqualTo mockedJournalpostId

                coVerify(exactly = 1) {
                    dokarkivMock.journalfor(
                        journalpostRequest = generateJournalpostRequest(
                            tittel = "Vurdering av §8-4 arbeidsuførhet",
                            brevkodeType = BrevkodeType.ARBEIDSUFORHET_VURDERING,
                            pdf = PDF_VURDERING,
                            vurderingUuid = vurderingOppfylt.uuid,
                            journalpostType = JournalpostType.NOTAT.name,
                        )
                    )
                }
            }

            it("Journalfører FORHANDSVARSEL vurdering") {
                val journalpostId = runBlocking {
                    journalforingService.journalfor(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        pdf = PDF_FORHANDSVARSEL,
                        vurdering = vurderingForhandsvarsel,
                    )
                }

                journalpostId shouldBeEqualTo mockedJournalpostId

                coVerify(exactly = 1) {
                    dokarkivMock.journalfor(
                        journalpostRequest = generateJournalpostRequest(
                            tittel = "Forhåndsvarsel om avslag på sykepenger",
                            brevkodeType = BrevkodeType.ARBEIDSUFORHET_FORHANDSVARSEL,
                            pdf = PDF_FORHANDSVARSEL,
                            vurderingUuid = vurderingForhandsvarsel.uuid,
                            journalpostType = JournalpostType.UTGAAENDE.name,
                        )
                    )
                }
            }

            it("Journalfører AVSLAG vurdering") {
                runBlocking {
                    assertFailsWith<IllegalStateException> {
                        journalforingService.journalfor(
                            personident = ARBEIDSTAKER_PERSONIDENT,
                            pdf = PDF_VURDERING,
                            vurdering = vurderingAvslag,
                        )
                    }
                }
            }
        }
    }
})
