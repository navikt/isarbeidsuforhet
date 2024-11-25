package no.nav.syfo.application.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS
import no.nav.syfo.UserConstants.PDF_AVSLAG
import no.nav.syfo.UserConstants.PDF_VURDERING
import no.nav.syfo.UserConstants.VEILEDER_IDENT
import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.application.IVurderingPdfService
import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.VurderingArsak
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.generator.generateVurdering
import no.nav.syfo.infrastructure.clients.pdfgen.VurderingPdfService
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVurdering
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import no.nav.syfo.infrastructure.kafka.VurderingProducer
import no.nav.syfo.infrastructure.kafka.VurderingRecord
import org.amshove.kluent.*
import org.amshove.kluent.internal.assertFailsWith
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.lang.RuntimeException
import java.time.LocalDate
import java.util.*
import java.util.concurrent.Future

val expiredForhandsvarsel = generateForhandsvarselVurdering(svarfrist = LocalDate.now().minusDays(1))

class VurderingServiceSpek : Spek({
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

        val mockVurderingProducer = mockk<KafkaProducer<String, VurderingRecord>>(relaxed = true)
        val vurderingProducer = VurderingProducer(
            producer = mockVurderingProducer,
        )

        val vurderingService = VurderingService(
            vurderingRepository = vurderingRepository,
            vurderingPdfService = vurderingPdfService,
            journalforingService = journalforingService,
            vurderingProducer = vurderingProducer,
        )
        val vurderingRepositoryMock = mockk<IVurderingRepository>(relaxed = true)
        val vurderingPdfServiceMock = mockk<IVurderingPdfService>(relaxed = true)
        val journalforingServiceMock = mockk<IJournalforingService>(relaxed = true)
        val vurderingServiceWithMocks = VurderingService(
            vurderingRepository = vurderingRepositoryMock,
            vurderingPdfService = vurderingPdfServiceMock,
            journalforingService = journalforingServiceMock,
            vurderingProducer = mockk<IVurderingProducer>(relaxed = true),
        )

        beforeEachTest {
            clearAllMocks()
            coEvery { mockVurderingProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
            coEvery {
                vurderingRepositoryMock.createVurdering(any(), any())
            } returns generateForhandsvarselVurdering()
            coEvery {
                vurderingPdfServiceMock.createVurderingPdf(any(), any())
            } returns PDF_FORHANDSVARSEL
            coEvery {
                journalforingServiceMock.journalfor(any(), any(), any())
            } returns 1
        }

        afterEachTest {
            database.dropData()
        }

        val vurderingForhandsvarsel = generateForhandsvarselVurdering()
        val vurderingOppfylt = generateVurdering(type = VurderingType.OPPFYLT)
        val vurderingAvslag = generateVurdering(type = VurderingType.AVSLAG)
        val vurderingIkkeAktuell = generateVurdering(type = VurderingType.IKKE_AKTUELL)

        describe("Journalføring") {
            it("journalfører FORHANDSVARSEL vurdering") {
                vurderingRepository.createVurdering(
                    pdf = PDF_FORHANDSVARSEL,
                    vurdering = vurderingForhandsvarsel,
                )

                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val journalfortVurdering = success.first().getOrThrow()
                journalfortVurdering.journalpostId?.value shouldBeEqualTo mockedJournalpostId.toString()

                val pVurdering = database.getVurdering(journalfortVurdering.uuid)
                pVurdering!!.updatedAt shouldBeGreaterThan pVurdering.createdAt
                pVurdering.type shouldBeEqualTo VurderingType.FORHANDSVARSEL.name
                pVurdering.journalpostId shouldBeEqualTo mockedJournalpostId.toString()
            }

            it("journalfører OPPFYLT vurdering") {
                vurderingRepository.createVurdering(
                    vurdering = vurderingOppfylt,
                    pdf = PDF_VURDERING,
                )

                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val journalfortVurdering = success.first().getOrThrow()
                journalfortVurdering.journalpostId?.value shouldBeEqualTo mockedJournalpostId.toString()

                val pVurdering = database.getVurdering(journalfortVurdering.uuid)
                pVurdering!!.updatedAt shouldBeGreaterThan pVurdering.createdAt
                pVurdering.type shouldBeEqualTo VurderingType.OPPFYLT.name
                pVurdering.journalpostId shouldBeEqualTo mockedJournalpostId.toString()
            }

            it("journalfører AVSLAG vurdering") {
                vurderingRepository.createVurdering(
                    vurdering = vurderingAvslag,
                    pdf = PDF_AVSLAG,
                )

                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val pVurdering = database.getVurdering(vurderingAvslag.uuid)
                pVurdering!!.type shouldBeEqualTo VurderingType.AVSLAG.name
                pVurdering.journalpostId shouldBeEqualTo mockedJournalpostId.toString()
            }

            it("journalfører IKKE_AKTUELL vurdering") {
                vurderingRepository.createVurdering(
                    vurdering = vurderingIkkeAktuell,
                    pdf = PDF_VURDERING,
                )

                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val journalfortVurdering = success.first().getOrThrow()
                journalfortVurdering.journalpostId?.value shouldBeEqualTo mockedJournalpostId.toString()

                val pVurdering = database.getVurdering(journalfortVurdering.uuid)
                pVurdering!!.updatedAt shouldBeGreaterThan pVurdering.createdAt
                pVurdering.type shouldBeEqualTo VurderingType.IKKE_AKTUELL.name
                pVurdering.journalpostId shouldBeEqualTo mockedJournalpostId.toString()
            }

            it("journalfører ikke når ingen vurderinger") {
                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0
            }

            it("journalfører ikke når vurdering allerede er journalført") {
                vurderingRepository.createVurdering(
                    pdf = PDF_FORHANDSVARSEL,
                    vurdering = vurderingForhandsvarsel,
                )
                val journalfortVarsel = vurderingForhandsvarsel.journalfor(journalpostId = JournalpostId(mockedJournalpostId.toString()))
                vurderingRepository.setJournalpostId(journalfortVarsel)

                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0
            }

            it("journalfører vurderinger selv om noen feiler") {
                val vurderingFails = generateVurdering(
                    type = VurderingType.FORHANDSVARSEL,
                    personident = ARBEIDSTAKER_PERSONIDENT_PDL_FAILS,
                    svarfrist = LocalDate.now().plusDays(21),
                )
                vurderingRepository.createVurdering(
                    pdf = PDF_FORHANDSVARSEL,
                    vurdering = vurderingForhandsvarsel,
                )
                vurderingRepository.createVurdering(
                    pdf = PDF_FORHANDSVARSEL,
                    vurdering = vurderingFails,
                )

                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 1
            }

            it("journalfører flere vurderinger av ulik type") {
                vurderingRepository.createVurdering(
                    pdf = PDF_FORHANDSVARSEL,
                    vurdering = vurderingForhandsvarsel,
                )
                vurderingRepository.createVurdering(
                    pdf = PDF_VURDERING,
                    vurdering = vurderingOppfylt,
                )

                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 2
            }
        }

        describe("publishUnpublishedVurderinger") {

            it("publishes unpublished vurdering") {
                vurderingRepository.createVurdering(
                    vurdering = expiredForhandsvarsel,
                    pdf = PDF_FORHANDSVARSEL,
                )
                val unpublishedAvslagVurdering = runBlocking {
                    vurderingService.createVurdering(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        veilederident = VEILEDER_IDENT,
                        type = VurderingType.AVSLAG,
                        arsak = null,
                        begrunnelse = "",
                        document = emptyList(),
                        gjelderFom = LocalDate.now().plusDays(1),
                        callId = UUID.randomUUID().toString(),
                    )
                }

                val (success, failed) = vurderingService.publishUnpublishedVurderinger().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 2

                val producerRecordSlot1 = slot<ProducerRecord<String, VurderingRecord>>()
                val producerRecordSlot2 = slot<ProducerRecord<String, VurderingRecord>>()
                verifyOrder {
                    mockVurderingProducer.send(capture(producerRecordSlot1))
                    mockVurderingProducer.send(capture(producerRecordSlot2))
                }

                val publishedAvslagVurdering = success[1].getOrThrow()
                publishedAvslagVurdering.uuid.shouldBeEqualTo(unpublishedAvslagVurdering.uuid)

                val persistedVurderinger = vurderingService.getVurderinger(ARBEIDSTAKER_PERSONIDENT)
                persistedVurderinger.size shouldBeEqualTo 2
                val persistedAvslagVurdering = persistedVurderinger[0]
                persistedAvslagVurdering.uuid shouldBeEqualTo unpublishedAvslagVurdering.uuid
                persistedAvslagVurdering.publishedAt.shouldNotBeNull()
                vurderingRepository.getUnpublishedVurderinger().shouldBeEmpty()

                val avslagVurderingRecord = producerRecordSlot2.captured.value()
                avslagVurderingRecord.uuid shouldBeEqualTo unpublishedAvslagVurdering.uuid
                avslagVurderingRecord.type shouldBeEqualTo unpublishedAvslagVurdering.type
                avslagVurderingRecord.gjelderFom shouldBeEqualTo unpublishedAvslagVurdering.gjelderFom
                avslagVurderingRecord.isFinal shouldBeEqualTo true

                val forhandsvarselRecord = producerRecordSlot1.captured.value()
                forhandsvarselRecord.isFinal shouldBeEqualTo false
            }

            it("publishes unpublished ikke-aktuell vurdering") {
                val unpublishedIkkeAktuellVurdering = runBlocking {
                    vurderingService.createVurdering(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        veilederident = VEILEDER_IDENT,
                        type = VurderingType.IKKE_AKTUELL,
                        arsak = VurderingArsak.FRISKMELDT,
                        begrunnelse = "",
                        document = emptyList(),
                        gjelderFom = null,
                        callId = UUID.randomUUID().toString(),
                    )
                }

                val (success, failed) = vurderingService.publishUnpublishedVurderinger().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val producerRecordSlot = slot<ProducerRecord<String, VurderingRecord>>()
                verifyOrder {
                    mockVurderingProducer.send(capture(producerRecordSlot))
                }

                val ikkeAktuellVurderingRecord = producerRecordSlot.captured.value()
                ikkeAktuellVurderingRecord.uuid shouldBeEqualTo unpublishedIkkeAktuellVurdering.uuid
                ikkeAktuellVurderingRecord.type shouldBeEqualTo unpublishedIkkeAktuellVurdering.type
                ikkeAktuellVurderingRecord.arsak shouldBeEqualTo unpublishedIkkeAktuellVurdering.arsak
                ikkeAktuellVurderingRecord.gjelderFom.shouldBeNull()
                ikkeAktuellVurderingRecord.isFinal shouldBeEqualTo true
            }

            it("publishes nothing when no unpublished vurdering") {
                val (success, failed) = vurderingService.publishUnpublishedVurderinger().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0
                verify(exactly = 0) { mockVurderingProducer.send(any()) }
            }

            it("fails publishing when kafka-producer fails") {
                val unpublishedVurdering = runBlocking {
                    vurderingService.createVurdering(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        veilederident = VEILEDER_IDENT,
                        type = VurderingType.FORHANDSVARSEL,
                        arsak = null,
                        begrunnelse = "",
                        document = emptyList(),
                        gjelderFom = null,
                        svarfrist = LocalDate.now().plusDays(21),
                        callId = UUID.randomUUID().toString(),
                    )
                }

                every { mockVurderingProducer.send(any()) } throws Exception("Error producing to kafka")

                val (success, failed) = vurderingService.publishUnpublishedVurderinger().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 0

                verify(exactly = 1) { mockVurderingProducer.send(any()) }

                val vurderingList = vurderingRepository.getUnpublishedVurderinger()
                vurderingList.size shouldBeEqualTo 1
                vurderingList.first().uuid.shouldBeEqualTo(unpublishedVurdering.uuid)
            }
        }

        describe("Vurdering") {
            val begrunnelse = "En begrunnelse"
            val document = generateDocumentComponent(begrunnelse)
            describe("Happy path") {
                it("lager vurdering FORHANDSVARSEL med pdf og varsel") {
                    coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_FORHANDSVARSEL

                    val vurdering = runBlocking {
                        vurderingServiceWithMocks.createVurdering(
                            personident = ARBEIDSTAKER_PERSONIDENT,
                            veilederident = VEILEDER_IDENT,
                            type = VurderingType.FORHANDSVARSEL,
                            arsak = null,
                            begrunnelse = begrunnelse,
                            document = document,
                            gjelderFom = null,
                            svarfrist = LocalDate.now().plusDays(21),
                            callId = "",
                        )
                    }

                    vurdering.varsel shouldNotBeEqualTo null
                    vurdering.type shouldBeEqualTo VurderingType.FORHANDSVARSEL
                    vurdering.journalpostId shouldBeEqualTo null
                    vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT

                    coVerify(exactly = 1) {
                        vurderingPdfServiceMock.createVurderingPdf(
                            vurdering = vurdering,
                            callId = "",
                        )
                    }
                }

                it("lager vurdering OPPFYLT med pdf") {
                    coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_VURDERING

                    val vurdering = runBlocking {
                        vurderingServiceWithMocks.createVurdering(
                            personident = ARBEIDSTAKER_PERSONIDENT,
                            veilederident = VEILEDER_IDENT,
                            type = VurderingType.OPPFYLT,
                            arsak = null,
                            begrunnelse = begrunnelse,
                            document = document,
                            gjelderFom = null,
                            callId = "",
                        )
                    }

                    vurdering.varsel shouldBeEqualTo null
                    vurdering.type shouldBeEqualTo VurderingType.OPPFYLT
                    vurdering.journalpostId shouldBeEqualTo null
                    vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT

                    coVerify(exactly = 1) {
                        vurderingPdfServiceMock.createVurderingPdf(
                            vurdering = vurdering,
                            callId = "",
                        )
                    }
                }

                it("lager vurdering AVSLAG med pdf") {
                    every { vurderingRepositoryMock.getVurderinger(ARBEIDSTAKER_PERSONIDENT) } returns listOf(expiredForhandsvarsel)
                    coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_AVSLAG
                    val avslagGjelderFom = LocalDate.now().plusDays(1)

                    val vurdering = runBlocking {
                        vurderingServiceWithMocks.createVurdering(
                            personident = ARBEIDSTAKER_PERSONIDENT,
                            veilederident = VEILEDER_IDENT,
                            type = VurderingType.AVSLAG,
                            arsak = null,
                            begrunnelse = "Avslag",
                            document = document,
                            gjelderFom = avslagGjelderFom,
                            callId = "",
                        )
                    }

                    vurdering.varsel shouldBeEqualTo null
                    vurdering.begrunnelse shouldBeEqualTo "Avslag"
                    vurdering.document shouldBeEqualTo document
                    vurdering.type shouldBeEqualTo VurderingType.AVSLAG
                    vurdering.journalpostId shouldBeEqualTo null
                    vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT
                    vurdering.gjelderFom shouldBeEqualTo avslagGjelderFom

                    coVerify(exactly = 1) {
                        vurderingPdfServiceMock.createVurderingPdf(
                            vurdering = vurdering,
                            callId = "",
                        )
                    }
                }

                it("lager vurdering IKKE_AKTUELL med pdf") {
                    coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_VURDERING

                    val vurdering = runBlocking {
                        vurderingServiceWithMocks.createVurdering(
                            personident = ARBEIDSTAKER_PERSONIDENT,
                            veilederident = VEILEDER_IDENT,
                            type = VurderingType.IKKE_AKTUELL,
                            arsak = VurderingArsak.FRISKMELDT,
                            begrunnelse = "",
                            document = document,
                            gjelderFom = null,
                            callId = "",
                        )
                    }

                    vurdering.varsel shouldBeEqualTo null
                    vurdering.type shouldBeEqualTo VurderingType.IKKE_AKTUELL
                    vurdering.journalpostId shouldBeEqualTo null
                    vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT

                    coVerify(exactly = 1) {
                        vurderingPdfServiceMock.createVurderingPdf(
                            vurdering = vurdering,
                            callId = "",
                        )
                    }
                }
            }

            describe("Unhappy path") {

                it("Fails when repository fails") {
                    coEvery {
                        vurderingRepositoryMock.createVurdering(any(), any())
                    } throws RuntimeException("Error in database")

                    runBlocking {
                        assertFailsWith(RuntimeException::class) {
                            vurderingServiceWithMocks.createVurdering(
                                personident = ARBEIDSTAKER_PERSONIDENT,
                                veilederident = VEILEDER_IDENT,
                                type = VurderingType.FORHANDSVARSEL,
                                arsak = null,
                                begrunnelse = begrunnelse,
                                document = document,
                                gjelderFom = null,
                                svarfrist = LocalDate.now().plusDays(21),
                                callId = "",
                            )
                        }

                        coVerify(exactly = 1) {
                            vurderingPdfServiceMock.createVurderingPdf(any(), any())
                        }
                        coVerify(exactly = 1) {
                            vurderingRepositoryMock.createVurdering(any(), any())
                        }
                    }
                }

                it("Fails when pdfGen fails") {
                    coEvery {
                        vurderingPdfServiceMock.createVurderingPdf(any(), any())
                    } throws RuntimeException("Could not create pdf")

                    runBlocking {
                        assertFailsWith(RuntimeException::class) {
                            vurderingServiceWithMocks.createVurdering(
                                personident = ARBEIDSTAKER_PERSONIDENT,
                                veilederident = VEILEDER_IDENT,
                                type = VurderingType.FORHANDSVARSEL,
                                arsak = null,
                                begrunnelse = begrunnelse,
                                document = document,
                                gjelderFom = null,
                                svarfrist = LocalDate.now().plusDays(21),
                                callId = "",
                            )
                        }

                        coVerify(exactly = 1) {
                            vurderingPdfServiceMock.createVurderingPdf(any(), any())
                        }
                        coVerify(exactly = 0) {
                            vurderingRepositoryMock.createVurdering(any(), any())
                        }
                    }
                }

                it("Avslag fails when current vurdering is not expired forhandsvarsel") {
                    vurderingRepository.createVurdering(
                        vurdering = generateForhandsvarselVurdering(),
                        pdf = PDF_FORHANDSVARSEL,
                    )

                    runBlocking {
                        assertFailsWith(IllegalArgumentException::class) {
                            vurderingServiceWithMocks.createVurdering(
                                personident = ARBEIDSTAKER_PERSONIDENT,
                                veilederident = VEILEDER_IDENT,
                                type = VurderingType.AVSLAG,
                                arsak = null,
                                begrunnelse = "",
                                document = emptyList(),
                                gjelderFom = LocalDate.now().plusDays(1),
                                callId = "",
                            )
                        }
                    }
                }

                it("Avslag fails when current vurdering is oppfylt") {
                    vurderingRepository.createVurdering(
                        vurdering = generateVurdering(type = VurderingType.OPPFYLT),
                        pdf = PDF_VURDERING,
                    )

                    runBlocking {
                        assertFailsWith(IllegalArgumentException::class) {
                            vurderingServiceWithMocks.createVurdering(
                                personident = ARBEIDSTAKER_PERSONIDENT,
                                veilederident = VEILEDER_IDENT,
                                type = VurderingType.AVSLAG,
                                arsak = null,
                                begrunnelse = "",
                                document = emptyList(),
                                gjelderFom = LocalDate.now().plusDays(1),
                                callId = "",
                            )
                        }
                    }
                }

                it("Avslag fails when no current vurdering") {
                    runBlocking {
                        assertFailsWith(IllegalArgumentException::class) {
                            vurderingServiceWithMocks.createVurdering(
                                personident = ARBEIDSTAKER_PERSONIDENT,
                                veilederident = VEILEDER_IDENT,
                                type = VurderingType.AVSLAG,
                                arsak = null,
                                begrunnelse = "",
                                document = emptyList(),
                                gjelderFom = LocalDate.now().plusDays(1),
                                callId = "",
                            )
                        }
                    }
                }

                it("Avslag fails when missing gjelderFom") {
                    every { vurderingRepositoryMock.getVurderinger(ARBEIDSTAKER_PERSONIDENT) } returns listOf(expiredForhandsvarsel)
                    coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_AVSLAG

                    runBlocking {
                        assertFailsWith(IllegalArgumentException::class) {
                            vurderingServiceWithMocks.createVurdering(
                                personident = ARBEIDSTAKER_PERSONIDENT,
                                veilederident = VEILEDER_IDENT,
                                type = VurderingType.AVSLAG,
                                arsak = null,
                                begrunnelse = "Avslag",
                                document = document,
                                gjelderFom = null,
                                callId = "",
                            )
                        }
                    }
                }

                it("Ikke aktuell fails when missing arsak") {
                    coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_VURDERING

                    runBlocking {
                        assertFailsWith(IllegalArgumentException::class) {
                            vurderingServiceWithMocks.createVurdering(
                                personident = ARBEIDSTAKER_PERSONIDENT,
                                veilederident = VEILEDER_IDENT,
                                type = VurderingType.IKKE_AKTUELL,
                                arsak = null,
                                begrunnelse = "",
                                document = document,
                                gjelderFom = null,
                                callId = "",
                            )
                        }
                    }
                }
            }
        }
    }
})
