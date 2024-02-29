package no.nav.syfo.application.service

import io.mockk.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.Varsel
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.generator.generateForhandsvarselVurdering
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.repository.VarselRepository
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.kafka.ExpiredForhandsvarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.ArbeidstakerForhandsvarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.ArbeidstakerHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.EsyfovarselHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.HendelseType
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.VarselData
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.concurrent.Future

private const val journalpostId = "123"

class VarselServiceSpek : Spek({
    describe(VarselService::class.java.simpleName) {

        val database = ExternalMockEnvironment.instance.database

        val varselRepository = VarselRepository(database = database)
        val vurderingRepository = VurderingRepository(database = database)
        val mockEsyfoVarselHendelseProducer = mockk<KafkaProducer<String, EsyfovarselHendelse>>()
        val mockExpiredForhandsvarselProducer = mockk<KafkaProducer<String, Varsel>>()

        val varselProducer = ArbeidstakerForhandsvarselProducer(kafkaProducer = mockEsyfoVarselHendelseProducer)
        val expiredForhandsvarselProducer =
            ExpiredForhandsvarselProducer(producer = mockExpiredForhandsvarselProducer)
        val varselService = VarselService(
            varselRepository = varselRepository,
            varselProducer = varselProducer,
            expiredForhandsvarselProducer = expiredForhandsvarselProducer,
        )

        beforeEachTest {
            clearMocks(mockEsyfoVarselHendelseProducer)
            clearMocks(mockExpiredForhandsvarselProducer)
            coEvery { mockEsyfoVarselHendelseProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
            coEvery { mockExpiredForhandsvarselProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
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

        fun createExpiredUnpublishedVarsel(): Varsel {
            val varselUnpublishedExpiredYesterday =
                Varsel(
                    generateDocumentComponent("En begrunnelse"),
                    svarfrist = OffsetDateTime.now().minusDays(1)
                )
            val vurdering = generateForhandsvarselVurdering().copy(varsel = varselUnpublishedExpiredYesterday)

            vurderingRepository.createForhandsvarsel(pdf = UserConstants.PDF_FORHANDSVARSEL, vurdering = vurdering)
            return vurdering.varsel!!
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
                publishedVarsel.journalpostId.shouldBeEqualTo(journalpostId)
                publishedVarsel.publishedAt.shouldNotBeNull()

                varselRepository.getUnpublishedVarsler().shouldBeEmpty()

                val esyfovarselHendelse = producerRecordSlot.captured.value() as ArbeidstakerHendelse
                esyfovarselHendelse.type.shouldBeEqualTo(HendelseType.SM_ARBEIDSUFORHET_FORHANDSVARSEL)
                esyfovarselHendelse.arbeidstakerFnr.shouldBeEqualTo(UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                val varselData = esyfovarselHendelse.data as VarselData
                varselData.journalpost?.uuid.shouldBeEqualTo(publishedVarsel.uuid.toString())
                varselData.journalpost?.id.shouldBeEqualTo(publishedVarsel.journalpostId)
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

                val (_, varsel) = varselRepository.getUnpublishedVarsler().first()
                varsel.uuid.shouldBeEqualTo(unpublishedVarsel.uuid)
            }
        }

        describe("publishExpiredForhandsvarsler") {

            it("publishes expired varsel") {
                val expiredUnpublishedVarsel = createExpiredUnpublishedVarsel()

                val (success, failed) = varselService.publishExpiredForhandsvarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val producerRecordSlot = slot<ProducerRecord<String, Varsel>>()
                verify(exactly = 1) { mockExpiredForhandsvarselProducer.send(capture(producerRecordSlot)) }

                val publishedExpiredVarsel = success.first().getOrThrow()
                publishedExpiredVarsel.uuid.shouldBeEqualTo(expiredUnpublishedVarsel.uuid)
                publishedExpiredVarsel.journalpostId.shouldBeEqualTo(expiredUnpublishedVarsel.journalpostId)
                publishedExpiredVarsel.svarfristExpiredPublishedAt.shouldNotBeNull()

                varselRepository.getUnpublishedVarsler().shouldBeEmpty()

                val publishedExpiredVarselRecord = producerRecordSlot.captured.value() as Varsel
                publishedExpiredVarselRecord.uuid shouldBeEqualTo expiredUnpublishedVarsel.uuid
            }

            it("publishes nothing when no expired unpublished varsel") {
                val publishedExpiredVarsel = createExpiredUnpublishedVarsel().publishExpiredVarsel()
                varselRepository.update(publishedExpiredVarsel)

                val (success, failed) = varselService.publishExpiredForhandsvarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0

                verify(exactly = 0) { mockExpiredForhandsvarselProducer.send(any()) }
            }

            it("fails publishing when kafka-producer fails") {
                val unpublishedExpiredVarsel = createExpiredUnpublishedVarsel()
                every { mockExpiredForhandsvarselProducer.send(any()) } throws Exception("Error producing to kafka")

                val (success, failed) = varselService.publishExpiredForhandsvarsler().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 0

                verify(exactly = 1) { mockExpiredForhandsvarselProducer.send(any()) }

                val (_, varsel) = varselRepository.getUnpublishedExpiredVarsler().first()
                varsel.uuid.shouldBeEqualTo(unpublishedExpiredVarsel.uuid)
            }
        }
    }
})
