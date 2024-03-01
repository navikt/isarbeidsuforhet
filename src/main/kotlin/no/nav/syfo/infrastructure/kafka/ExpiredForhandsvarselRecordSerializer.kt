package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.domain.Varsel
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class ExpiredForhandsvarselRecordSerializer : Serializer<Varsel> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: Varsel?): ByteArray =
        mapper.writeValueAsBytes(data)
}
