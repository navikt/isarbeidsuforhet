package no.nav.syfo.infrastructure.kafka.esyfovarsel

import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.EsyfovarselHendelse
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaArbeidstakervarselSerializer : Serializer<EsyfovarselHendelse> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: EsyfovarselHendelse?): ByteArray =
        mapper.writeValueAsBytes(data)
}
