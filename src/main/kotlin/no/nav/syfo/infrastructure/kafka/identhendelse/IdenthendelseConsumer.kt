package no.nav.syfo.infrastructure.kafka.identhendelse

import kotlinx.coroutines.delay
import no.nav.syfo.infrastructure.kafka.KafkaConsumerService
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class IdenthendelseConsumer(private val identhendelseService: IdenthendelseService) : KafkaConsumerService<GenericRecord> {
    override val pollDurationInMillis: Long = 1000

    override suspend fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, GenericRecord>) {
        runCatching {
            val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
            if (records.count() > 0) {
                records.mapNotNull { it.value() }.forEach {
                    identhendelseService.handle(identhendelse = it.toKafkaIdenthendelseDTO())
                }
                kafkaConsumer.commitSync()
            }
        }.onFailure { ex ->
            log.warn("Error running kafka consumer for pdl-aktor, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry", ex)
            kafkaConsumer.unsubscribe()
            delay(DELAY_ON_ERROR_SECONDS.seconds)
        }
    }

    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
