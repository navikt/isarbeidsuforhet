package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.service.VurderingService
import org.slf4j.LoggerFactory

class JournalforVurderingerCronjob(
    private val vurderingService: VurderingService,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val (success, failed) = vurderingService.journalforVurderinger().partition { it.isSuccess }
        failed.forEach {
            log.error("Exception caught while journalforing vurdering", it.exceptionOrNull())
        }
        log.info(
            "Completed journalforing vurdering with result: {}, {}",
            StructuredArguments.keyValue("failed", failed.size),
            StructuredArguments.keyValue("updated", success.size),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforVurderingerCronjob::class.java)
    }
}
