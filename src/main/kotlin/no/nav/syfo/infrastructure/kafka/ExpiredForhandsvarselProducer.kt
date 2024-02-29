package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.IExpiredForhandsvarslerProducer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class ExpiredForhandsvarselProducer(private val producer: KafkaProducer<String, String>) :
    IExpiredForhandsvarslerProducer {

    override fun send(personIdent: PersonIdent, varsel: Varsel): Result<Varsel> =
        try {
            producer.send(
                ProducerRecord(
                    TOPIC,
                    UUID.randomUUID().toString(),
                    "",
                )
            ).get()
            Result.success(varsel)
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send expired forhandsvarsel: ${e.message}")
            Result.failure(e)
        }

    companion object {
        private const val TOPIC = "teamsykefravr.arbeidsuforhet-expired-forhandsvarsel"
        private val log = LoggerFactory.getLogger(ExpiredForhandsvarselProducer::class.java)
    }
}
