package no.nav.syfo.application.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.application.IVarselPdfService
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.generator.generateVarsel
import no.nav.syfo.infrastructure.database.VarselRepository
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.kafka.esyfovarsel.ArbeidstakervarselProducer
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.lang.RuntimeException
import java.util.concurrent.Future

private const val journalpostId = "123"
private val unpublishedVarsel = generateVarsel().copy(journalpostId = journalpostId)

class VarselServiceSpek : Spek({
    describe(VarselService::class.java.simpleName) {

        val database = ExternalMockEnvironment.instance.database

        val varselRepository = mockk<VarselRepository>(relaxed = true)
        val vurderingRepositoryMock = mockk<IVurderingRepository>(relaxed = true)
        val varselPdfServiceMock = mockk<IVarselPdfService>(relaxed = true)
        val kafkaProducer = mockk<KafkaProducer<String, String>>()

        val varselProducer = ArbeidstakervarselProducer(kafkaArbeidstakervarselProducer = kafkaProducer)
        val varselService = VarselService(
            vurderingRepository = vurderingRepositoryMock,
            varselPdfService = varselPdfServiceMock,
            varselRepository = varselRepository,
            varselProducer = varselProducer
        )

        beforeEachTest {
            clearAllMocks()
            coEvery {
                kafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
            coEvery {
                vurderingRepositoryMock.createForhandsvarsel(any(), any())
            } returns Unit
            coEvery {
                varselPdfServiceMock.createVarselPdf(any(), any(), any())
            } returns UserConstants.PDF_FORHANDSVARSEL
        }
        afterEachTest {
            database.dropData()
        }

        describe("Forhåndsvarsel") {
            val begrunnelse = "En begrunnelse"
            val document = generateDocumentComponent(begrunnelse)
            describe("Happy path") {
                it("Creates forhåndsvarsel") {
                    runBlocking {
                        val vurdering = varselService.createForhandsvarsel(
                            personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            veilederident = UserConstants.VEILEDER_IDENT,
                            begrunnelse = begrunnelse,
                            document = document,
                            callId = "",
                        )

                        vurdering.varsel shouldNotBeEqualTo null
                        vurdering.begrunnelse shouldBeEqualTo begrunnelse
                        vurdering.personident shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT
                        vurdering.veilederident shouldBeEqualTo UserConstants.VEILEDER_IDENT

                        coVerify(exactly = 1) {
                            varselPdfServiceMock.createVarselPdf(
                                personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                                document = document,
                                callId = "",
                            )
                        }
                        coVerify(exactly = 1) {
                            vurderingRepositoryMock.createForhandsvarsel(
                                pdf = UserConstants.PDF_FORHANDSVARSEL,
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
                            varselService.createForhandsvarsel(
                                personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                                veilederident = UserConstants.VEILEDER_IDENT,
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
                            varselService.createForhandsvarsel(
                                personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                                veilederident = UserConstants.VEILEDER_IDENT,
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

        describe("publishUnpublishedVarsler") {

            it("publishes unpublished varsel") {
                every { varselRepository.getUnpublishedVarsler() } returns listOf(Pair(UserConstants.ARBEIDSTAKER_PERSONIDENT, unpublishedVarsel))

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                verify(exactly = 1) { kafkaProducer.send(any()) }
                verify(exactly = 1) { varselRepository.update(unpublishedVarsel) }

                val publishedVarsel = success.first().getOrThrow()
                publishedVarsel.publishedAt.shouldNotBeNull()

                // TODO: Test kafkaproducer record value and published_at in database
            }

            it("publishes nothing when no unpublished varsel") {
                every { varselRepository.getUnpublishedVarsler() } returns emptyList()

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0

                verify(exactly = 0) { kafkaProducer.send(any()) }
                verify(exactly = 0) { varselRepository.update(any()) }
            }

            it("fails publishing when kafka-producer fails") {
                every { varselRepository.getUnpublishedVarsler() } returns listOf(Pair(UserConstants.ARBEIDSTAKER_PERSONIDENT, unpublishedVarsel))
                every { kafkaProducer.send(any()) } throws Exception("Error producing to kafka")

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 0

                verify(exactly = 1) { kafkaProducer.send(any()) }
                verify(exactly = 0) { varselRepository.update(any()) }
            }

            it("fails publishing when repository fails") {
                every { varselRepository.getUnpublishedVarsler() } returns listOf(Pair(UserConstants.ARBEIDSTAKER_PERSONIDENT, unpublishedVarsel))
                every { varselRepository.update(any()) } throws Exception("Error set published")

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 0

                verify(exactly = 1) { kafkaProducer.send(any()) }
                verify(exactly = 1) { varselRepository.update(any()) }
            }
        }
    }
})
