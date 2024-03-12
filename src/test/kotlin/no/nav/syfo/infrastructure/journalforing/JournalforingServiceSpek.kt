package no.nav.syfo.infrastructure.journalforing

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.PDF_OPPFYLT
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.domain.getBrevkode
import no.nav.syfo.domain.getDokumentTittel
import no.nav.syfo.domain.getJournalpostType
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.generator.generateJournalpostRequest
import no.nav.syfo.generator.generateVurdering
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
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
                runBlocking {
                    val journalpostId = journalforingService.journalfor(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        pdf = PDF_OPPFYLT,
                        vurdering = vurderingOppfylt,
                    )

                    journalpostId shouldBeEqualTo mockedJournalpostId

                    coVerify(exactly = 1) {
                        dokarkivMock.journalfor(
                            journalpostRequest = generateJournalpostRequest(
                                tittel = VurderingType.OPPFYLT.getDokumentTittel(),
                                brevkodeType = VurderingType.OPPFYLT.getBrevkode(),
                                pdf = PDF_OPPFYLT,
                                vurderingUuid = vurderingOppfylt.uuid,
                                journalpostType = VurderingType.OPPFYLT.getJournalpostType().name,
                            )
                        )
                    }
                }
            }

            it("Journalfører FORHANDSVARSEL vurdering") {
                runBlocking {
                    val journalpostId = journalforingService.journalfor(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        pdf = PDF_FORHANDSVARSEL,
                        vurdering = vurderingForhandsvarsel,
                    )

                    journalpostId shouldBeEqualTo mockedJournalpostId

                    coVerify(exactly = 1) {
                        dokarkivMock.journalfor(
                            journalpostRequest = generateJournalpostRequest(
                                tittel = VurderingType.FORHANDSVARSEL.getDokumentTittel(),
                                brevkodeType = VurderingType.FORHANDSVARSEL.getBrevkode(),
                                pdf = PDF_FORHANDSVARSEL,
                                vurderingUuid = vurderingForhandsvarsel.uuid,
                                journalpostType = VurderingType.FORHANDSVARSEL.getJournalpostType().name,
                            )
                        )
                    }
                }
            }

            it("Journalfører ikke når AVSLAG vurdering") {
                runBlocking {
                    assertFailsWith<IllegalStateException> {
                        journalforingService.journalfor(
                            personident = ARBEIDSTAKER_PERSONIDENT,
                            pdf = byteArrayOf(0x2E, 0x21),
                            vurdering = vurderingAvslag,
                        )
                    }

                    coVerify(exactly = 0) { dokarkivMock.journalfor(any()) }
                }
            }
        }
    }
})
