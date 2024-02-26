package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.service.VarselService
import org.slf4j.LoggerFactory

class PubliserForhandsvarselCronjob(
    private val varselService: VarselService,
) : Cronjob {
    override val initialDelayMinutes: Long = 5
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed publishing forhandsvarsel with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()
        val unpublishedVarsler = varselService.getUnpublished()
        unpublishedVarsler.forEach {
            try {
                varselService.publish(it)
                result.updated++
            } catch (e: Exception) {
                log.error("Exception caught while publishing forhandsvarsel", e)
                result.failed++
            }
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(PubliserForhandsvarselCronjob::class.java)
    }
}
