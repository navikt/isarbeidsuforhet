package no.nav.syfo.infrastructure.kafka.identhendelse

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.infrastructure.kafka.launchKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.*

const val PDL_AKTOR_TOPIC = "pdl.aktor-v2"

fun launchIdenthendelseConsumer(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    identhendelseConsumer: IdenthendelseConsumer,
) {
    val consumerProperties = Properties().apply {
        putAll(kafkaAivenConsumerConfig<KafkaAvroDeserializer>(kafkaEnvironment))
        this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"

        this[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = kafkaEnvironment.aivenSchemaRegistryUrl
        this[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = false
        this[KafkaAvroDeserializerConfig.USER_INFO_CONFIG] =
            "${kafkaEnvironment.aivenRegistryUser}:${kafkaEnvironment.aivenRegistryPassword}"
        this[KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
    }

    launchKafkaConsumer(
        applicationState = applicationState,
        topic = PDL_AKTOR_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = identhendelseConsumer
    )
}
