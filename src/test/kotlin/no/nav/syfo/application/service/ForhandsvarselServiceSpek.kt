package no.nav.syfo.application.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.VEILEDER_IDENT
import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.application.IVurderingPdfService
import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.generator.generateDocumentComponent
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.lang.RuntimeException

object ForhandsvarselServiceSpek : Spek({

    describe(ForhandsvarselServiceSpek::class.java.simpleName) {
        val vurderingRepositoryMock = mockk<IVurderingRepository>(relaxed = true)
        val varselPdfServiceMock = mockk<IVurderingPdfService>(relaxed = true)
        val journalforingServiceMock = mockk<IJournalforingService>(relaxed = true)
        val vurderingService = VurderingService(
            vurderingRepository = vurderingRepositoryMock,
            vurderingPdfService = varselPdfServiceMock,
            journalforingService = journalforingServiceMock,
            vurderingProducer = mockk<IVurderingProducer>(),
            svarfristDager = 21,
        )

        beforeEachTest {
            clearAllMocks()
            coEvery {
                vurderingRepositoryMock.createForhandsvarsel(any(), any())
            } returns Unit
            coEvery {
                varselPdfServiceMock.createVurderingPdf(any(), any(), any())
            } returns PDF_FORHANDSVARSEL
            coEvery {
                journalforingServiceMock.journalfor(any(), any(), any())
            } returns 1
        }

        describe("Forhåndsvarsel") {
            val begrunnelse = "En begrunnelse"
            val document = generateDocumentComponent(begrunnelse)
            describe("Happy path") {
                it("Creates forhåndsvarsel") {
                    runBlocking {
                        val vurdering = vurderingService.createForhandsvarsel(
                            personident = ARBEIDSTAKER_PERSONIDENT,
                            veilederident = VEILEDER_IDENT,
                            begrunnelse = begrunnelse,
                            document = document,
                            callId = "",
                        )

                        vurdering.varsel shouldNotBeEqualTo null
                        vurdering.begrunnelse shouldBeEqualTo begrunnelse
                        vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT
                        vurdering.veilederident shouldBeEqualTo VEILEDER_IDENT

                        coVerify(exactly = 1) {
                            varselPdfServiceMock.createVurderingPdf(
                                personident = ARBEIDSTAKER_PERSONIDENT,
                                document = document,
                                callId = "",
                            )
                        }
                        coVerify(exactly = 1) {
                            vurderingRepositoryMock.createForhandsvarsel(
                                pdf = PDF_FORHANDSVARSEL,
                                vurdering = any(),
                            )
                        }
                    }
                }
            }

            describe("Unhappy path") {
                it("Fails when repository fails") {
                    coEvery {
                        vurderingRepositoryMock.createForhandsvarsel(any(), any())
                    } throws Exception("Error in database")

                    runBlocking {
                        assertFailsWith(Exception::class) {
                            vurderingService.createForhandsvarsel(
                                personident = ARBEIDSTAKER_PERSONIDENT,
                                veilederident = VEILEDER_IDENT,
                                begrunnelse = begrunnelse,
                                document = document,
                                callId = "",
                            )
                        }

                        coVerify(exactly = 1) {
                            varselPdfServiceMock.createVurderingPdf(any(), any(), any())
                        }
                        coVerify(exactly = 1) {
                            vurderingRepositoryMock.createForhandsvarsel(any(), any())
                        }
                    }
                }

                it("Fails when pdfGen fails") {
                    coEvery {
                        varselPdfServiceMock.createVurderingPdf(any(), any(), any())
                    } throws RuntimeException("Could not create pdf")

                    runBlocking {
                        assertFailsWith(RuntimeException::class) {
                            vurderingService.createForhandsvarsel(
                                personident = ARBEIDSTAKER_PERSONIDENT,
                                veilederident = VEILEDER_IDENT,
                                begrunnelse = begrunnelse,
                                document = document,
                                callId = "",
                            )
                        }

                        coVerify(exactly = 1) {
                            varselPdfServiceMock.createVurderingPdf(any(), any(), any())
                        }
                        coVerify(exactly = 0) {
                            vurderingRepositoryMock.createForhandsvarsel(any(), any())
                        }
                    }
                }
            }
        }
    }
})
