package no.nav.syfo.application.service

import io.mockk.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.model.UnpublishedVarsel
import no.nav.syfo.infrastructure.database.VarselRepository
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.kafka.esyfovarsel.ArbeidstakervarselProducer
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*
import java.util.concurrent.Future

private const val journalpostId = "123"
private val varselUuid = UUID.randomUUID()
private val unpublishedVarsel = UnpublishedVarsel(
    personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    varselUuid = varselUuid,
    journalpostId = journalpostId
)

class VarselServiceSpek : Spek({
    describe(VarselService::class.java.simpleName) {

        val database = ExternalMockEnvironment.instance.database

        val varselRepository = mockk<VarselRepository>(relaxed = true)
        val kafkaProducer = mockk<KafkaProducer<String, String>>()

        val varselProducer = ArbeidstakervarselProducer(kafkaArbeidstakervarselProducer = kafkaProducer)
        val varselService = VarselService(varselRepository = varselRepository, varselProducer = varselProducer)

        beforeEachTest {
            clearMocks(kafkaProducer, varselRepository)
            coEvery {
                kafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }
        afterEachTest {
            database.dropData()
        }

        describe("publishUnpublishedVarsler") {

            it("publishes unpublished varsel") {
                every { varselRepository.getUnpublishedVarsler() } returns listOf(unpublishedVarsel)

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                verify(exactly = 1) { kafkaProducer.send(any()) }
                verify(exactly = 1) { varselRepository.setPublished(unpublishedVarsel) }

                // TODO: Test kafkaproducer record value and published_at in database
            }

            it("publishes nothing when no unpublished varsel") {
                every { varselRepository.getUnpublishedVarsler() } returns emptyList()

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0

                verify(exactly = 0) { kafkaProducer.send(any()) }
                verify(exactly = 0) { varselRepository.setPublished(any()) }
            }

            it("fails publishing when kafka-producer fails") {
                every { varselRepository.getUnpublishedVarsler() } returns listOf(unpublishedVarsel)
                every { kafkaProducer.send(any()) } throws Exception("Error producing to kafka")

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 0

                verify(exactly = 1) { kafkaProducer.send(any()) }
                verify(exactly = 0) { varselRepository.setPublished(any()) }
            }

            it("fails publishing when repository fails") {
                every { varselRepository.getUnpublishedVarsler() } returns listOf(unpublishedVarsel)
                every { varselRepository.setPublished(any()) } throws Exception("Error set published")

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 0

                verify(exactly = 1) { kafkaProducer.send(any()) }
                verify(exactly = 1) { varselRepository.setPublished(any()) }
            }
        }
    }
})
