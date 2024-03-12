package no.nav.syfo.application.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.UserConstants.PDF_OPPFYLT
import no.nav.syfo.UserConstants.VEILEDER_IDENT
import no.nav.syfo.domain.JournalpostId
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
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*
import java.util.concurrent.Future

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
            svarfristDager = externalMockEnvironment.environment.svarfristDager,
        )

        beforeEachTest {
            clearAllMocks()
            coEvery { mockVurderingProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }

        afterEachTest {
            database.dropData()
        }

        val vurderingForhandsvarsel = generateForhandsvarselVurdering()

        describe("Journalføring") {
            it("journalfører forhåndsvarsel") {
                vurderingRepository.createForhandsvarsel(
                    pdf = UserConstants.PDF_FORHANDSVARSEL,
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
                val vurderingOppfylt = generateVurdering(type = VurderingType.OPPFYLT)
                vurderingRepository.createVurdering(
                    vurdering = vurderingOppfylt,
                    pdf = PDF_OPPFYLT,
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

            it("journalfører ikke når ingen forhåndsvarsler") {
                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0
            }

            it("journalfører ikke når forhåndsvarsel allerede er journalført") {
                vurderingRepository.createForhandsvarsel(
                    pdf = UserConstants.PDF_FORHANDSVARSEL,
                    vurdering = vurderingForhandsvarsel,
                )
                val journalfortVarsel = vurderingForhandsvarsel.journalfor(journalpostId = JournalpostId(mockedJournalpostId.toString()))
                vurderingRepository.update(journalfortVarsel)

                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0
            }

            it("journalfører forhåndsvarsel selv om noen feiler") {
                vurderingRepository.createForhandsvarsel(
                    pdf = UserConstants.PDF_FORHANDSVARSEL,
                    vurdering = vurderingForhandsvarsel,
                )

                val vurderingFails = generateForhandsvarselVurdering(
                    personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS,
                )
                vurderingRepository.createForhandsvarsel(
                    pdf = UserConstants.PDF_FORHANDSVARSEL,
                    vurdering = vurderingFails,
                )

                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 1
            }
        }
        describe("publishUnpublishedVurderinger") {

            it("publishes unpublished vurdering") {
                val unpublishedVurdering = runBlocking {
                    vurderingService.createForhandsvarsel(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        veilederident = VEILEDER_IDENT,
                        begrunnelse = "",
                        document = emptyList(),
                        callId = UUID.randomUUID().toString(),
                    )
                }

                val (success, failed) = vurderingService.publishUnpublishedVurderinger().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val producerRecordSlot = slot<ProducerRecord<String, VurderingRecord>>()
                verify(exactly = 1) { mockVurderingProducer.send(capture(producerRecordSlot)) }

                val publishedVurdering = success.first().getOrThrow()
                publishedVurdering.uuid.shouldBeEqualTo(unpublishedVurdering.uuid)

                val persistedVurderinger = vurderingService.getVurderinger(ARBEIDSTAKER_PERSONIDENT)
                persistedVurderinger.size shouldBeEqualTo 1
                val persistedVurdering = persistedVurderinger.first()
                persistedVurdering.uuid shouldBeEqualTo unpublishedVurdering.uuid
                persistedVurdering.publishedAt.shouldNotBeNull()
                vurderingRepository.getUnpublishedVurderinger().shouldBeEmpty()
            }

            it("publishes nothing when no unpublished vurdering") {
                val (success, failed) = vurderingService.publishUnpublishedVurderinger().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0
                verify(exactly = 0) { mockVurderingProducer.send(any()) }
            }

            it("fails publishing when kafka-producer fails") {
                val unpublishedVurdering = runBlocking {
                    vurderingService.createForhandsvarsel(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        veilederident = VEILEDER_IDENT,
                        begrunnelse = "",
                        document = emptyList(),
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

            it("journalfører ikke når kun AVSLAG vurderinger") {
                val vurderingAvslag = generateVurdering(type = VurderingType.AVSLAG)
                vurderingRepository.createVurdering(
                    vurdering = vurderingAvslag,
                    pdf = null,
                )

                val journalforteVurderinger = runBlocking {
                    vurderingService.journalforVurderinger()
                }

                val (success, failed) = journalforteVurderinger.partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0

                val pVurdering = database.getVurdering(vurderingAvslag.uuid)
                pVurdering?.type shouldBeEqualTo VurderingType.AVSLAG.name
                pVurdering?.journalpostId shouldBeEqualTo null
            }
        }

        describe("Vurdering") {
            val vurderingPdfServiceMock = mockk<VurderingPdfService>(relaxed = true)
            val vurderingServiceWithMock = VurderingService(
                vurderingRepository = vurderingRepository,
                vurderingPdfService = vurderingPdfServiceMock,
                journalforingService = journalforingService,
                vurderingProducer = vurderingProducer,
                svarfristDager = externalMockEnvironment.environment.svarfristDager
            )
            it("lager vurdering OPPFYLT med pdf") {
                coEvery { vurderingPdfServiceMock.createVurderingPdf(any(), any()) } returns PDF_OPPFYLT

                val vurdering = runBlocking {
                    vurderingServiceWithMock.createVurdering(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        veilederident = VEILEDER_IDENT,
                        type = VurderingType.OPPFYLT,
                        begrunnelse = "En begrunnelse",
                        document = generateDocumentComponent("En begrunnelse"),
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

            it("lager vurdering AVSLAG uten pdf") {
                val vurdering = runBlocking {
                    vurderingServiceWithMock.createVurdering(
                        personident = ARBEIDSTAKER_PERSONIDENT,
                        veilederident = VEILEDER_IDENT,
                        type = VurderingType.AVSLAG,
                        begrunnelse = "En begrunnelse",
                        document = generateDocumentComponent("En begrunnelse"),
                        callId = "",
                    )
                }

                vurdering.varsel shouldBeEqualTo null
                vurdering.type shouldBeEqualTo VurderingType.AVSLAG
                vurdering.journalpostId shouldBeEqualTo null
                vurdering.personident shouldBeEqualTo ARBEIDSTAKER_PERSONIDENT

                coVerify(exactly = 0) { vurderingPdfServiceMock.createVurderingPdf(any(), any()) }
            }
        }
    }
})
