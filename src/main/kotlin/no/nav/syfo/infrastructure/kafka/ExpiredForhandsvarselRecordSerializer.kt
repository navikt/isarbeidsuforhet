package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class ExpiredForhandsvarselRecordSerializer : Serializer<ExpiredForhandsvarselRecord> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: ExpiredForhandsvarselRecord?): ByteArray =
        mapper.writeValueAsBytes(data)
}
