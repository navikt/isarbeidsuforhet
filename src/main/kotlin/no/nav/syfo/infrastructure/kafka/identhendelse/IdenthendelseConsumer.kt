package no.nav.syfo.infrastructure.kafka.identhendelse

import no.nav.syfo.infrastructure.kafka.KafkaConsumerService
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class IdenthendelseConsumer(private val identhendelseService: IdenthendelseService) : KafkaConsumerService<GenericRecord> {
    override val pollDurationInMillis: Long = 1000

    override suspend fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, GenericRecord>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            records.mapNotNull { it.value() }.forEach {
                identhendelseService.handle(identhendelse = it.toKafkaIdenthendelseDTO())
            }
            kafkaConsumer.commitSync()
        }
    }
}
