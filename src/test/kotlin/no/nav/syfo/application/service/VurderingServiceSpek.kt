package no.nav.syfo.application.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.clients.pdfgen.VurderingPdfService
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVurdering
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.journalforing.JournalforingService
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
        describe("publishUnpublishedVurderinger") {

            it("publishes unpublished vurdering") {
                val unpublishedVurdering = runBlocking {
                    vurderingService.createForhandsvarsel(
                        personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        veilederident = UserConstants.VEILEDER_IDENT,
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

                val persistedVurderinger = vurderingService.getVurderinger(UserConstants.ARBEIDSTAKER_PERSONIDENT)
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
                        personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        veilederident = UserConstants.VEILEDER_IDENT,
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
        }
    }
})
