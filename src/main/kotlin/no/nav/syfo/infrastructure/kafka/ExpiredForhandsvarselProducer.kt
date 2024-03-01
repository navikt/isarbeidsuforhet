package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.IExpiredForhandsvarselProducer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.*

class ExpiredForhandsvarselProducer(private val producer: KafkaProducer<String, ExpiredForhandsvarselRecord>) :
    IExpiredForhandsvarselProducer {

    override fun send(personIdent: PersonIdent, varsel: Varsel): Result<Varsel> =
        try {
            producer.send(
                ProducerRecord(
                    TOPIC,
                    UUID.randomUUID().toString(),
                    ExpiredForhandsvarselRecord.fromVarsel(personIdent, varsel),
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

data class ExpiredForhandsvarselRecord(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdent,
    val svarfrist: OffsetDateTime,
) {
    companion object {
        fun fromVarsel(personIdent: PersonIdent, varsel: Varsel): ExpiredForhandsvarselRecord =
            ExpiredForhandsvarselRecord(
                uuid = varsel.uuid,
                createdAt = varsel.createdAt,
                personIdent = personIdent,
                svarfrist = varsel.svarfrist,
            )
    }
}
