package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class VurderingRecordSerializer : Serializer<VurderingRecord> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: VurderingRecord?): ByteArray =
        mapper.writeValueAsBytes(data)
}
