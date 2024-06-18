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
import no.nav.syfo.infrastructure.kafka.VarselProducer
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
import java.util.concurrent.Future

private val journalpostId = JournalpostId("123")

class VarselServiceSpek : Spek({
    describe(VarselService::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val varselRepository = VarselRepository(database = database)
        val vurderingRepository = VurderingRepository(database = database)
        val mockEsyfoVarselHendelseProducer = mockk<KafkaProducer<String, EsyfovarselHendelse>>()
        val arbeidstakerForhandsvarselProducer =
            ArbeidstakerForhandsvarselProducer(kafkaProducer = mockEsyfoVarselHendelseProducer)
        val varselProducer = VarselProducer(
            arbeidstakerForhandsvarselProducer = arbeidstakerForhandsvarselProducer,
        )
        val varselService = VarselService(
            varselRepository = varselRepository,
            varselProducer = varselProducer,
        )

        beforeEachTest {
            clearAllMocks()
            coEvery { mockEsyfoVarselHendelseProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
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
            vurderingRepository.setJournalpostId(vurderingForhandsvarsel.copy(journalpostId = journalpostId))

            return unpublishedVarsel
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
    }
})
