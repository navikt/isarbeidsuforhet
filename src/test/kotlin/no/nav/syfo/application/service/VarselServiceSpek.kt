package no.nav.syfo.application.service

import io.mockk.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.Varsel
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.database.repository.VarselRepository
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.kafka.esyfovarsel.ArbeidstakervarselProducer
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Future

private const val journalpostId = "123"

class VarselServiceSpek : Spek({
    describe(VarselService::class.java.simpleName) {

        val database = ExternalMockEnvironment.instance.database

        val varselRepository = VarselRepository(database = database)
        val vurderingRepository = VurderingRepository(database = database)
        val kafkaProducer = mockk<KafkaProducer<String, String>>()

        val varselProducer = ArbeidstakervarselProducer(kafkaArbeidstakervarselProducer = kafkaProducer)
        val varselService = VarselService(varselRepository = varselRepository, varselProducer = varselProducer)

        beforeEachTest {
            clearMocks(kafkaProducer)
            coEvery {
                kafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }
        afterEachTest {
            database.dropData()
        }

        fun createUnpublishedVarsel(): Varsel {
            val vurdering = generateForhandsvarselVurdering()
            vurderingRepository.createForhandsvarsel(pdf = UserConstants.PDF_FORHANDSVARSEL, vurdering = vurdering)
            val unpublishedVarsel = vurdering.varsel?.copy(journalpostId = journalpostId)!!
            varselRepository.update(varsel = unpublishedVarsel)

            return unpublishedVarsel
        }

        describe("publishUnpublishedVarsler") {

            it("publishes unpublished varsel") {
                val unpublishedVarsel = createUnpublishedVarsel()

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                verify(exactly = 1) { kafkaProducer.send(any()) }

                val publishedVarsel = success.first().getOrThrow()
                publishedVarsel.uuid.shouldBeEqualTo(unpublishedVarsel.uuid)
                publishedVarsel.journalpostId.shouldBeEqualTo(journalpostId)
                publishedVarsel.publishedAt.shouldNotBeNull()

                varselRepository.getUnpublishedVarsler().shouldBeEmpty()

                // TODO: Test kafkaproducer record value
            }

            it("publishes nothing when no unpublished varsel") {
                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0

                verify(exactly = 0) { kafkaProducer.send(any()) }
            }

            it("fails publishing when kafka-producer fails") {
                val unpublishedVarsel = createUnpublishedVarsel()
                every { kafkaProducer.send(any()) } throws Exception("Error producing to kafka")

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 0

                verify(exactly = 1) { kafkaProducer.send(any()) }

                val (_, varsel) = varselRepository.getUnpublishedVarsler().first()
                varsel.uuid.shouldBeEqualTo(unpublishedVarsel.uuid)
            }
        }
    }
})
