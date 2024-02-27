package no.nav.syfo.infrastructure.kafka.esyfovarsel

import no.nav.syfo.application.IVarselProducer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import java.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

// TODO: Add esyfovarsel-hendelse as kafkaproducer value
class ArbeidstakervarselProducer(private val kafkaArbeidstakervarselProducer: KafkaProducer<String, String>) : IVarselProducer {

    override fun sendArbeidstakerVarsel(personIdent: PersonIdent, varsel: Varsel) {
        try {
            kafkaArbeidstakervarselProducer.send(
                ProducerRecord(
                    ESYFOVARSEL_TOPIC,
                    UUID.randomUUID().toString(),
                    "",
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send hendelse to esyfovarsel: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val ESYFOVARSEL_TOPIC = "team-esyfo.varselbus"
        private val log = LoggerFactory.getLogger(ArbeidstakervarselProducer::class.java)
    }
}
