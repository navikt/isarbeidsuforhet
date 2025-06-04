package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.domain.VurderingArsak
import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.domain.VurderingType
import no.nav.syfo.domain.asProducerRecordKey
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class VurderingProducer(private val producer: KafkaProducer<String, VurderingRecord>) :
    IVurderingProducer {

    override fun send(vurdering: Vurdering): Result<Vurdering> =
        try {
            producer.send(
                ProducerRecord(
                    TOPIC,
                    vurdering.personident.asProducerRecordKey(),
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
    val personident: String,
    val veilederident: String,
    val type: VurderingType,
    val arsak: VurderingArsak?,
    val begrunnelse: String,
    val gjelderFom: LocalDate?,
    val isFinal: Boolean,
) {
    companion object {
        fun fromVurdering(vurdering: Vurdering): VurderingRecord =
            VurderingRecord(
                uuid = vurdering.uuid,
                createdAt = vurdering.createdAt,
                personident = vurdering.personident.value,
                veilederident = vurdering.veilederident,
                type = vurdering.type,
                arsak = vurdering.arsak()?.let { VurderingArsak.valueOf(it) },
                begrunnelse = vurdering.begrunnelse,
                gjelderFom = vurdering.gjelderFom,
                isFinal = vurdering.type.isFinal
            )
    }
}
