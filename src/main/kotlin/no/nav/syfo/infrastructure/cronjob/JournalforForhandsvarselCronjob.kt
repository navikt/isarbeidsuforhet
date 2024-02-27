package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.service.VarselService
import org.slf4j.LoggerFactory

class JournalforForhandsvarselCronjob(
    private val varselService: VarselService,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val (success, failed) = varselService.journalforVarsler().partition { it.isSuccess }
        failed.forEach {
            log.error("Exception caught while journalforing forhandsvarsel", it.exceptionOrNull())
        }
        log.info(
            "Completed journalforing forhandsvarsel with result: {}, {}",
            StructuredArguments.keyValue("failed", failed.size),
            StructuredArguments.keyValue("updated", success.size),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforForhandsvarselCronjob::class.java)
    }
}
