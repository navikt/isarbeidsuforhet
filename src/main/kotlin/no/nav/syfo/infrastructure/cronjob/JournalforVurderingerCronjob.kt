package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.service.VurderingService

class JournalforVurderingerCronjob(
    private val vurderingService: VurderingService,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 1

    override suspend fun run() = vurderingService.journalforVurderinger()
}
