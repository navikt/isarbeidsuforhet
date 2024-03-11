package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.service.VarselService
import org.slf4j.LoggerFactory

class PublishForhandsvarselCronjob(
    private val varselService: VarselService,
) : Cronjob {
    override val initialDelayMinutes: Long = 5
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val (success, failed) = varselService.publishUnpublishedVarsler().partition { it.isSuccess }
        failed.forEach {
            log.error("Exception caught while publishing forhandsvarsel", it.exceptionOrNull())
        }
        log.info(
            "Completed publishing forhandsvarsel with result: {}, {}",
            StructuredArguments.keyValue("failed", failed.size),
            StructuredArguments.keyValue("updated", success.size),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishForhandsvarselCronjob::class.java)
    }
}
