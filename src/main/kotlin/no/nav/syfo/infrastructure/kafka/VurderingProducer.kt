package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.domain.VurderingType
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.*

class VurderingProducer(private val producer: KafkaProducer<String, VurderingRecord>) :
    IVurderingProducer {

    override fun send(vurdering: Vurdering): Result<Vurdering> =
        try {
            producer.send(
                ProducerRecord(
                    TOPIC,
                    UUID.randomUUID().toString(),
                    VurderingRecord.fromVurdering(vurdering),
                )
            ).get()
            Result.success(vurdering)
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send vurdering: ${e.message}")
            Result.failure(e)
        }

    companion object {
        private const val TOPIC = "teamsykefravr.arbeidsuforhet-vurdering"
        private val log = LoggerFactory.getLogger(VurderingProducer::class.java)
    }
}

data class VurderingRecord(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personident: PersonIdent,
    val veilederident: String,
    val type: VurderingType,
    val begrunnelse: String,
) {
    companion object {
        fun fromVurdering(vurdering: Vurdering): VurderingRecord =
            VurderingRecord(
                uuid = vurdering.uuid,
                createdAt = vurdering.createdAt,
                personident = vurdering.personident,
                veilederident = vurdering.veilederident,
                type = vurdering.type,
                begrunnelse = vurdering.begrunnelse,
            )
    }
}
