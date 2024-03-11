package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.service.VurderingService
import org.slf4j.LoggerFactory

class PublishVurderingerCronjob(
    private val vurderingService: VurderingService,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 1

    override suspend fun run() {
        val (success, failed) = vurderingService.publishUnpublishedVurderinger().partition { it.isSuccess }
        failed.forEach {
            log.error("Exception caught while publishing vurdering", it.exceptionOrNull())
        }
        log.info(
            "Completed publishing vurdering with result: {}, {}",
            StructuredArguments.keyValue("failed", failed.size),
            StructuredArguments.keyValue("updated", success.size),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishVurderingerCronjob::class.java)
    }
}
