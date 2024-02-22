package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.infrastructure.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.pdl.PdlClient
import org.slf4j.LoggerFactory

class JournalforForhandsvarselCronjob(
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed journalføring of varsel with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()
        // Hent ikke-journalførte forhåndsvarsler

        // For alle ikke-journalførte forhåndsvarsler
        // - Hent navn fra pdl
        // - Journalfør
        // - Registrer at forhåndsvarselet er journalført
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforForhandsvarselCronjob::class.java)
    }
}
