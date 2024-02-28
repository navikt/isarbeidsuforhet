package no.nav.syfo.infrastructure.kafka.esyfovarsel

import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.*
import java.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class ArbeidstakerForhandsvarselProducer(private val kafkaProducer: KafkaProducer<String, EsyfovarselHendelse>) : IVarselProducer {

    override fun sendArbeidstakerForhandsvarsel(personIdent: PersonIdent, varsel: Varsel) {
        val varselHendelse = ArbeidstakerHendelse(
            type = HendelseType.SM_ARBEIDSUFORHET_FORHANDSVARSEL,
            arbeidstakerFnr = personIdent.value,
            data = VarselData(
                journalpost = VarselDataJournalpost(
                    uuid = varsel.uuid.toString(),
                    id = varsel.journalpostId,
                ),
            ),
            orgnummer = null,
        )

        try {
            kafkaProducer.send(
                ProducerRecord(
                    ESYFOVARSEL_TOPIC,
                    UUID.randomUUID().toString(),
                    varselHendelse,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send hendelse to esyfovarsel: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val ESYFOVARSEL_TOPIC = "team-esyfo.varselbus"
        private val log = LoggerFactory.getLogger(ArbeidstakerForhandsvarselProducer::class.java)
    }
}
