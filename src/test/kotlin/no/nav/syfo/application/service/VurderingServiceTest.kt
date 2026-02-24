package no.nav.syfo.application.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS
import no.nav.syfo.UserConstants.PDF_AVSLAG
import no.nav.syfo.UserConstants.PDF_FORHANDSVARSEL
import no.nav.syfo.UserConstants.PDF_VURDERING
import no.nav.syfo.UserConstants.VEILEDER_IDENT
import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.application.IVurderingPdfService
import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.Vurdering
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
import no.nav.syfo.infrastructure.kafka.VurderingProducer
import no.nav.syfo.infrastructure.kafka.VurderingRecord
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDate
import java.util.*
import java.util.concurrent.Future

class VurderingServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    private val vurderingRepository = VurderingRepository(database = database)
    private val vurderingPdfService = VurderingPdfService(
        externalMockEnvironment.pdfgenClient,
        externalMockEnvironment.pdlClient,
    )
    private val journalforingService = JournalforingService(
        dokarkivClient = externalMockEnvironment.dokarkivClient,
        pdlClient = externalMockEnvironment.pdlClient,
        isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
    )
    private val mockVurderingProducer = mockk<KafkaProducer<String, VurderingRecord>>(relaxed = true)
    private val vurderingProducer = VurderingProducer(
        producer = mockVurderingProducer,
    )
    private val vurderingService = VurderingService(
        vurderingRepository = vurderingRepository,
        vurderingPdfService = vurderingPdfService,
        journalforingService = journalforingService,
        vurderingProducer = vurderingProducer,
    )
    private val vurderingRepositoryMock = mockk<IVurderingRepository>(relaxed = true)
    private val vurderingPdfServiceMock = mockk<IVurderingPdfService>(relaxed = true)
    private val journalforingServiceMock = mockk<IJournalforingService>(relaxed = true)
    private val vurderingServiceWithMocks = VurderingService(
        vurderingRepository = vurderingRepositoryMock,
        vurderingPdfService = vurderingPdfServiceMock,
        journalforingService = journalforingServiceMock,
        vurderingProducer = mockk<IVurderingProducer>(relaxed = true),
    )

    private val vurderingForhandsvarsel = generateForhandsvarselVurdering()
    private val vurderingOppfylt = generateVurdering(type = VurderingType.OPPFYLT)
    private val vurderingAvslag = generateVurdering(type = VurderingType.AVSLAG)
    private val vurderingAvslagUtenForhandsvarsel = generateVurdering(type = VurderingType.AVSLAG_UTEN_FORHANDSVARSEL)
    private val vurderingIkkeAktuell = generateVurdering(type = VurderingType.IKKE_AKTUELL)
    private val expiredForhandsvarsel = generateForhandsvarselVurdering(svarfrist = LocalDate.now().minusDays(1))

    @BeforeEach
    fun setup() {
        clearAllMocks()
        coEvery { mockVurderingProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        coEvery { vurderingRepositoryMock.createVurdering(any(), any()) } returns generateForhandsvarselVurdering()
        coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_FORHANDSVARSEL
        coEvery { journalforingServiceMock.journalfor(any(), any(), any()) } returns 1
    }

    @AfterEach
    fun cleanup() {
        database.dropData()
    }

    @Nested
    @DisplayName("Journalfør vurderinger")
    inner class Journalforing {
        @Test
        fun `journalfører FORHANDSVARSEL vurdering`() {
            vurderingRepository.createVurdering(pdf = PDF_FORHANDSVARSEL, vurdering = vurderingForhandsvarsel)
            val results = runBlocking { vurderingService.journalforVurderinger() }
            val (success, failed) = results.partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(1, success.size)
            val journalfort = success.first().getOrThrow()
            assertEquals(mockedJournalpostId.toString(), journalfort.journalpostId?.value)
            val p = database.getVurdering(journalfort.uuid)!!
            assertTrue(p.updatedAt > p.createdAt)
            assertEquals(VurderingType.FORHANDSVARSEL.name, p.type)
            assertEquals(mockedJournalpostId.toString(), p.journalpostId)
        }

        @Test
        fun `journalfører OPPFYLT vurdering`() {
            vurderingRepository.createVurdering(vurdering = vurderingOppfylt, pdf = PDF_VURDERING)
            val journalforteVurderinger = runBlocking { vurderingService.journalforVurderinger() }
            val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(1, success.size)
            val journalfort = success.first().getOrThrow()
            assertEquals(mockedJournalpostId.toString(), journalfort.journalpostId?.value)
            val p = database.getVurdering(journalfort.uuid)!!
            assertTrue(p.updatedAt > p.createdAt)
            assertEquals(VurderingType.OPPFYLT.name, p.type)
            assertEquals(mockedJournalpostId.toString(), p.journalpostId)
        }

        @Test
        fun `journalfører AVSLAG vurdering`() {
            vurderingRepository.createVurdering(vurdering = vurderingAvslag, pdf = PDF_AVSLAG)
            val journalforteVurderinger = runBlocking { vurderingService.journalforVurderinger() }
            val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(1, success.size)
            val p = database.getVurdering(vurderingAvslag.uuid)!!
            assertEquals(VurderingType.AVSLAG.name, p.type)
            assertEquals(mockedJournalpostId.toString(), p.journalpostId)
        }

        @Test
        fun `journalfører AVSLAG_UTEN_FORHANDSVARSEL vurdering`() {
            vurderingRepository.createVurdering(vurdering = vurderingAvslagUtenForhandsvarsel, pdf = PDF_AVSLAG)
            val journalforteVurderinger = runBlocking { vurderingService.journalforVurderinger() }
            val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(1, success.size)
            val p = database.getVurdering(vurderingAvslagUtenForhandsvarsel.uuid)!!
            assertEquals(VurderingType.AVSLAG_UTEN_FORHANDSVARSEL.name, p.type)
            assertEquals(mockedJournalpostId.toString(), p.journalpostId)
        }

        @Test
        fun `journalfører IKKE_AKTUELL vurdering`() {
            vurderingRepository.createVurdering(vurdering = vurderingIkkeAktuell, pdf = PDF_VURDERING)
            val journalforteVurderinger = runBlocking { vurderingService.journalforVurderinger() }
            val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(1, success.size)
            val journalfort = success.first().getOrThrow()
            assertEquals(mockedJournalpostId.toString(), journalfort.journalpostId?.value)
            val p = database.getVurdering(journalfort.uuid)!!
            assertTrue(p.updatedAt > p.createdAt)
            assertEquals(VurderingType.IKKE_AKTUELL.name, p.type)
            assertEquals(mockedJournalpostId.toString(), p.journalpostId)
        }

        @Test
        fun `journalfører ikke når ingen vurderinger`() {
            val journalforteVurderinger = runBlocking { vurderingService.journalforVurderinger() }
            val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(0, success.size)
        }

        @Test
        fun `journalfører ikke når vurdering allerede er journalført`() {
            vurderingRepository.createVurdering(pdf = PDF_FORHANDSVARSEL, vurdering = vurderingForhandsvarsel)
            val journalfort =
                vurderingForhandsvarsel.journalfor(journalpostId = JournalpostId(mockedJournalpostId.toString()))
            vurderingRepository.setJournalpostId(journalfort)
            val journalforteVurderinger = runBlocking { vurderingService.journalforVurderinger() }
            val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(0, success.size)
        }

        @Test
        fun `journalfører vurderinger selv om noen feiler`() {
            val vurderingFails = generateVurdering(
                type = VurderingType.FORHANDSVARSEL,
                personident = ARBEIDSTAKER_PERSONIDENT_PDL_FAILS,
                svarfrist = LocalDate.now().plusDays(21),
            )
            vurderingRepository.createVurdering(pdf = PDF_FORHANDSVARSEL, vurdering = vurderingForhandsvarsel)
            vurderingRepository.createVurdering(pdf = PDF_FORHANDSVARSEL, vurdering = vurderingFails)
            val journalforteVurderinger = runBlocking { vurderingService.journalforVurderinger() }
            val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
            assertEquals(1, failed.size)
            assertEquals(1, success.size)
        }

        @Test
        fun `journalfører flere vurderinger av ulik type`() {
            vurderingRepository.createVurdering(pdf = PDF_FORHANDSVARSEL, vurdering = vurderingForhandsvarsel)
            vurderingRepository.createVurdering(pdf = PDF_VURDERING, vurdering = vurderingOppfylt)
            val journalforteVurderinger = runBlocking { vurderingService.journalforVurderinger() }
            val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(2, success.size)
        }
    }

    @Nested
    @DisplayName("Publish unpublished vurderinger")
    inner class PublishUnpublishedVurderinger {
        @Test
        fun `publishes unpublished vurdering`() {
            vurderingRepository.createVurdering(vurdering = expiredForhandsvarsel, pdf = PDF_FORHANDSVARSEL)
            val unpublishedAvslag = runBlocking {
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
            assertEquals(0, failed.size)
            assertEquals(2, success.size)
            val slot1 = slot<ProducerRecord<String, VurderingRecord>>()
            val slot2 = slot<ProducerRecord<String, VurderingRecord>>()
            verifyOrder {
                mockVurderingProducer.send(capture(slot1))
                mockVurderingProducer.send(capture(slot2))
            }
            val publishedAvslag = success[1].getOrThrow()
            assertEquals(unpublishedAvslag.uuid, publishedAvslag.uuid)
            val persisted = vurderingService.getVurderinger(ARBEIDSTAKER_PERSONIDENT)
            assertEquals(2, persisted.size)
            val persistedAvslag = persisted[0]
            assertEquals(unpublishedAvslag.uuid, persistedAvslag.uuid)
            assertNotNull(persistedAvslag.publishedAt)
            assertTrue(vurderingRepository.getUnpublishedVurderinger().isEmpty())
            val avslagRecord = slot2.captured.value()
            assertEquals(unpublishedAvslag.uuid, avslagRecord.uuid)
            assertEquals(unpublishedAvslag.type, avslagRecord.type)
            assertEquals((unpublishedAvslag as Vurdering.Avslag).gjelderFom, avslagRecord.gjelderFom)
            assertTrue(avslagRecord.isFinal)
            val forhandsvarselRecord = slot1.captured.value()
            assertFalse(forhandsvarselRecord.isFinal)
        }

        @Test
        fun `publishes unpublished ikke-aktuell vurdering`() {
            val unpublishedIkkeAktuell = runBlocking {
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
            assertEquals(0, failed.size)
            assertEquals(1, success.size)
            val slot1 = slot<ProducerRecord<String, VurderingRecord>>()
            verifyOrder { mockVurderingProducer.send(capture(slot1)) }
            val record = slot1.captured.value()
            assertEquals(unpublishedIkkeAktuell.uuid, record.uuid)
            assertEquals(unpublishedIkkeAktuell.type, record.type)
            assertEquals((unpublishedIkkeAktuell as Vurdering.IkkeAktuell).arsak.name, record.arsak?.name)
            assertNull(record.gjelderFom)
            assertTrue(record.isFinal)
        }

        @Test
        fun `publishes nothing when no unpublished vurdering`() {
            val (success, failed) = vurderingService.publishUnpublishedVurderinger().partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(0, success.size)
            verify(exactly = 0) { mockVurderingProducer.send(any()) }
        }

        @Test
        fun `fails publishing when kafka-producer fails`() {
            val unpublished = runBlocking {
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
            assertEquals(1, failed.size)
            assertEquals(0, success.size)
            verify(exactly = 1) { mockVurderingProducer.send(any()) }
            val remaining = vurderingRepository.getUnpublishedVurderinger()
            assertEquals(1, remaining.size)
            assertEquals(unpublished.uuid, remaining.first().uuid)
        }
    }

    @Nested
    @DisplayName("Create vurdering")
    inner class CreateVurdering {
        private val begrunnelse = "En begrunnelse"
        private val document = generateDocumentComponent(begrunnelse)

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {
            @Test
            fun `lager vurdering FORHANDSVARSEL med pdf og varsel`() {
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
                assertNotNull((vurdering as Vurdering.Forhandsvarsel).varsel)
                assertEquals(VurderingType.FORHANDSVARSEL, vurdering.type)
                assertNull(vurdering.journalpostId)
                assertEquals(ARBEIDSTAKER_PERSONIDENT, vurdering.personident)
                coVerify(exactly = 1) { vurderingPdfServiceMock.createVurderingPdf(vurdering = vurdering, callId = "") }
            }

            @Test
            fun `lager ikke vurdering FORHANDSVARSEL med for kort svarfrist`() {
                coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_FORHANDSVARSEL
                assertThrows<IllegalArgumentException> {
                    runBlocking {
                        vurderingServiceWithMocks.createVurdering(
                            personident = ARBEIDSTAKER_PERSONIDENT,
                            veilederident = VEILEDER_IDENT,
                            type = VurderingType.FORHANDSVARSEL,
                            arsak = null,
                            begrunnelse = begrunnelse,
                            document = document,
                            gjelderFom = null,
                            svarfrist = LocalDate.now().plusDays(20),
                            callId = "",
                        )
                    }
                }
            }

            @Test
            fun `lager ikke vurdering FORHANDSVARSEL med for lang svarfrist`() {
                coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_FORHANDSVARSEL
                assertThrows<IllegalArgumentException> {
                    runBlocking {
                        vurderingServiceWithMocks.createVurdering(
                            personident = ARBEIDSTAKER_PERSONIDENT,
                            veilederident = VEILEDER_IDENT,
                            type = VurderingType.FORHANDSVARSEL,
                            arsak = null,
                            begrunnelse = begrunnelse,
                            document = document,
                            gjelderFom = null,
                            svarfrist = LocalDate.now().plusDays(43),
                            callId = "",
                        )
                    }
                }
            }

            @Test
            fun `lager vurdering OPPFYLT med pdf`() {
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
                assertEquals(VurderingType.OPPFYLT, vurdering.type)
                assertNull(vurdering.journalpostId)
                assertEquals(ARBEIDSTAKER_PERSONIDENT, vurdering.personident)
                coVerify(exactly = 1) { vurderingPdfServiceMock.createVurderingPdf(vurdering = vurdering, callId = "") }
            }

            @Test
            fun `lager vurdering AVSLAG med pdf`() {
                every { vurderingRepositoryMock.getVurderinger(ARBEIDSTAKER_PERSONIDENT) } returns listOf(
                    expiredForhandsvarsel
                )
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
                assertEquals("Avslag", vurdering.begrunnelse)
                assertEquals(document, vurdering.document)
                assertEquals(VurderingType.AVSLAG, vurdering.type)
                assertNull(vurdering.journalpostId)
                assertEquals(ARBEIDSTAKER_PERSONIDENT, vurdering.personident)
                assertEquals(avslagGjelderFom, (vurdering as Vurdering.Avslag).gjelderFom)
                coVerify(exactly = 1) { vurderingPdfServiceMock.createVurderingPdf(vurdering = vurdering, callId = "") }
            }

            @Test
            fun `lager vurdering IKKE_AKTUELL med pdf`() {
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
                assertEquals(VurderingType.IKKE_AKTUELL, vurdering.type)
                assertNull(vurdering.journalpostId)
                assertEquals(ARBEIDSTAKER_PERSONIDENT, vurdering.personident)
                coVerify(exactly = 1) { vurderingPdfServiceMock.createVurderingPdf(vurdering = vurdering, callId = "") }
            }
        }

        @Nested
        @DisplayName("Unhappy path")
        inner class UnhappyPath {
            @Test
            fun `Fails when repository fails`() {
                coEvery {
                    vurderingRepositoryMock.createVurdering(
                        any(),
                        any()
                    )
                } throws RuntimeException("Error in database")
                assertThrows<RuntimeException> {
                    runBlocking {
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
                }
                coVerify(exactly = 1) { vurderingPdfServiceMock.createVurderingPdf(any(), any()) }
                coVerify(exactly = 1) { vurderingRepositoryMock.createVurdering(any(), any()) }
            }

            @Test
            fun `Fails when pdfGen fails`() {
                coEvery {
                    vurderingPdfServiceMock.createVurderingPdf(
                        any(),
                        any()
                    )
                } throws RuntimeException("Could not create pdf")
                assertThrows<RuntimeException> {
                    runBlocking {
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
                }
                coVerify(exactly = 1) { vurderingPdfServiceMock.createVurderingPdf(any(), any()) }
                coVerify(exactly = 0) { vurderingRepositoryMock.createVurdering(any(), any()) }
            }

            @Test
            fun `Avslag fails when current vurdering is not expired forhandsvarsel`() {
                vurderingRepository.createVurdering(
                    vurdering = generateForhandsvarselVurdering(),
                    pdf = PDF_FORHANDSVARSEL
                )
                assertThrows<IllegalArgumentException> {
                    runBlocking {
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

            @Test
            fun `Avslag fails when current vurdering is oppfylt`() {
                vurderingRepository.createVurdering(
                    vurdering = generateVurdering(type = VurderingType.OPPFYLT),
                    pdf = PDF_VURDERING
                )
                assertThrows<IllegalArgumentException> {
                    runBlocking {
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

            @Test
            fun `Avslag fails when no current vurdering`() {
                assertThrows<IllegalArgumentException> {
                    runBlocking {
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

            @Test
            fun `Avslag fails when missing gjelderFom`() {
                every { vurderingRepositoryMock.getVurderinger(ARBEIDSTAKER_PERSONIDENT) } returns listOf(
                    expiredForhandsvarsel
                )
                coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_AVSLAG
                assertThrows<IllegalArgumentException> {
                    runBlocking {
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

            @Test
            fun `Ikke aktuell fails when missing arsak`() {
                coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_VURDERING
                assertThrows<IllegalArgumentException> {
                    runBlocking {
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
