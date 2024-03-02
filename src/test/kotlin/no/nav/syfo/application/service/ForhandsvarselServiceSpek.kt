package no.nav.syfo.application.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.VEILEDER_IDENT
import no.nav.syfo.application.IVarselPdfService
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
        val varselPdfServiceMock = mockk<IVarselPdfService>(relaxed = true)
        val vurderingService = VurderingService(
            vurderingRepository = vurderingRepositoryMock,
            varselPdfService = varselPdfServiceMock,
        )

        beforeEachTest {
            clearAllMocks()
            coEvery {
                vurderingRepositoryMock.createForhandsvarsel(any(), any())
            } returns Unit
            coEvery {
                varselPdfServiceMock.createVarselPdf(any(), any(), any())
            } returns PDF_FORHANDSVARSEL
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
                            varselPdfServiceMock.createVarselPdf(
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
                            varselPdfServiceMock.createVarselPdf(any(), any(), any())
                        }
                        coVerify(exactly = 1) {
                            vurderingRepositoryMock.createForhandsvarsel(any(), any())
                        }
                    }
                }

                it("Fails when pdfGen fails") {
                    coEvery {
                        varselPdfServiceMock.createVarselPdf(any(), any(), any())
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
                            varselPdfServiceMock.createVarselPdf(any(), any(), any())
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
