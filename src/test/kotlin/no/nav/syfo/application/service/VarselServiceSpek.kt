package no.nav.syfo.application.service

import io.mockk.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.Varsel
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.repository.VarselRepository
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.kafka.ExpiredForhandsvarselProducer
import no.nav.syfo.infrastructure.kafka.ExpiredForhandsvarselRecord
import no.nav.syfo.infrastructure.kafka.VarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.ArbeidstakerForhandsvarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.ArbeidstakerHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.EsyfovarselHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.HendelseType
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.VarselData
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Future

private val journalpostId = JournalpostId("123")

class VarselServiceSpek : Spek({
    describe(VarselService::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val varselRepository = VarselRepository(database = database)
        val vurderingRepository = VurderingRepository(database = database)
        val mockEsyfoVarselHendelseProducer = mockk<KafkaProducer<String, EsyfovarselHendelse>>()
        val mockExpiredForhandsvarselProducer = mockk<KafkaProducer<String, ExpiredForhandsvarselRecord>>()

        val arbeidstakerForhandsvarselProducer =
            ArbeidstakerForhandsvarselProducer(kafkaProducer = mockEsyfoVarselHendelseProducer)
        val expiredForhandsvarselProducer =
            ExpiredForhandsvarselProducer(producer = mockExpiredForhandsvarselProducer)
        val varselProducer = VarselProducer(
            arbeidstakerForhandsvarselProducer = arbeidstakerForhandsvarselProducer,
            expiredForhandsvarselProducer = expiredForhandsvarselProducer,
        )
        val varselService = VarselService(
            varselRepository = varselRepository,
            varselProducer = varselProducer,
        )

        beforeEachTest {
            clearAllMocks()
            coEvery { mockEsyfoVarselHendelseProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
            coEvery { mockExpiredForhandsvarselProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }
        afterEachTest {
            database.dropData()
        }

        val vurderingForhandsvarsel = generateForhandsvarselVurdering()

        fun createUnpublishedVarsel(): Varsel {
            vurderingRepository.createVurdering(
                pdf = UserConstants.PDF_FORHANDSVARSEL,
                vurdering = vurderingForhandsvarsel,
            )
            val unpublishedVarsel = vurderingForhandsvarsel.varsel
            vurderingRepository.update(vurderingForhandsvarsel.copy(journalpostId = journalpostId))

            return unpublishedVarsel
        }

        fun createExpiredUnpublishedVarsel(
            publishedAt: OffsetDateTime? = OffsetDateTime.now().minusDays(2)
        ): Varsel {
            val varselUnpublishedExpiredYesterday = Varsel().copy(svarfrist = LocalDate.now().minusDays(1))
            val vurderingWithExpiredVarsel = vurderingForhandsvarsel.copy(
                varsel = varselUnpublishedExpiredYesterday,
                uuid = UUID.randomUUID(),
            )

            vurderingRepository.createVurdering(
                pdf = UserConstants.PDF_FORHANDSVARSEL,
                vurdering = vurderingWithExpiredVarsel
            )
            val publishedVarsel = vurderingWithExpiredVarsel.varsel.copy(publishedAt = publishedAt)
            varselRepository.update(publishedVarsel)
            return publishedVarsel
        }

        describe("publishUnpublishedVarsler") {

            it("publishes unpublished varsel") {
                val unpublishedVarsel = createUnpublishedVarsel()

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val producerRecordSlot = slot<ProducerRecord<String, EsyfovarselHendelse>>()
                verify(exactly = 1) { mockEsyfoVarselHendelseProducer.send(capture(producerRecordSlot)) }

                val publishedVarsel = success.first().getOrThrow()
                publishedVarsel.uuid.shouldBeEqualTo(unpublishedVarsel.uuid)
                publishedVarsel.publishedAt.shouldNotBeNull()

                varselRepository.getUnpublishedVarsler().shouldBeEmpty()

                val esyfovarselHendelse = producerRecordSlot.captured.value() as ArbeidstakerHendelse
                esyfovarselHendelse.type.shouldBeEqualTo(HendelseType.SM_ARBEIDSUFORHET_FORHANDSVARSEL)
                esyfovarselHendelse.arbeidstakerFnr.shouldBeEqualTo(UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                val varselData = esyfovarselHendelse.data as VarselData
                varselData.journalpost?.uuid.shouldBeEqualTo(publishedVarsel.uuid.toString())
                varselData.journalpost?.id!!.shouldBeEqualTo(journalpostId.value)
            }

            it("publishes nothing when no unpublished varsel") {
                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0

                verify(exactly = 0) { mockEsyfoVarselHendelseProducer.send(any()) }
            }

            it("fails publishing when kafka-producer fails") {
                val unpublishedVarsel = createUnpublishedVarsel()
                every { mockEsyfoVarselHendelseProducer.send(any()) } throws Exception("Error producing to kafka")

                val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 0

                verify(exactly = 1) { mockEsyfoVarselHendelseProducer.send(any()) }

                val (_, _, varsel) = varselRepository.getUnpublishedVarsler().first()
                varsel.uuid.shouldBeEqualTo(unpublishedVarsel.uuid)
            }
        }

        describe("publishExpiredForhandsvarsler") {

            it("publishes expired varsel") {
                val expiredUnpublishedVarsel = createExpiredUnpublishedVarsel()

                val (success, failed) = varselService.publishExpiredForhandsvarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val producerRecordSlot = slot<ProducerRecord<String, ExpiredForhandsvarselRecord>>()
                verify(exactly = 1) { mockExpiredForhandsvarselProducer.send(capture(producerRecordSlot)) }

                val publishedExpiredVarsel = success.first().getOrThrow()
                publishedExpiredVarsel.uuid.shouldBeEqualTo(expiredUnpublishedVarsel.uuid)
                publishedExpiredVarsel.svarfristExpiredPublishedAt.shouldNotBeNull()

                varselRepository.getUnpublishedVarsler().shouldBeEmpty()

                val publishedExpiredVarselRecord = producerRecordSlot.captured.value() as ExpiredForhandsvarselRecord
                publishedExpiredVarselRecord.uuid shouldBeEqualTo expiredUnpublishedVarsel.uuid
            }

            it("publishes nothing when no expired unpublished varsel") {
                val publishedExpiredVarsel = createExpiredUnpublishedVarsel().publishSvarfristExpired()
                varselRepository.update(publishedExpiredVarsel)

                val (success, failed) = varselService.publishExpiredForhandsvarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0

                verify(exactly = 0) { mockExpiredForhandsvarselProducer.send(any()) }
            }

            it("publishes nothing when expired varsel has not been sent") {
                val unpublishedExpiredVarsel = createExpiredUnpublishedVarsel(publishedAt = null)
                varselRepository.update(unpublishedExpiredVarsel)

                val (success, failed) = varselService.publishExpiredForhandsvarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0

                verify(exactly = 0) { mockExpiredForhandsvarselProducer.send(any()) }
            }

            it("fails publishing for varsler for which kafka-producer fails, others are published") {
                val unpublishedExpiredVarsel = createExpiredUnpublishedVarsel()
                val unpublishedExpiredVarselAnother = createExpiredUnpublishedVarsel()
                unpublishedExpiredVarsel.uuid shouldNotBeEqualTo unpublishedExpiredVarselAnother.uuid

                every { mockExpiredForhandsvarselProducer.send(match { it.value().uuid == unpublishedExpiredVarsel.uuid }) } throws Exception("Error producing to kafka")

                val (success, failed) = varselService.publishExpiredForhandsvarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 1

                verify(exactly = 2) { mockExpiredForhandsvarselProducer.send(any()) }

                val (_, varsel) = varselRepository.getUnpublishedExpiredVarsler().first()
                varsel.uuid.shouldBeEqualTo(unpublishedExpiredVarsel.uuid)
            }
        }
    }
})
