package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.service.VurderingService

class PublishVurderingerCronjob(
    private val vurderingService: VurderingService,
) : Cronjob {
    override val initialDelayMinutes: Long = 5
    override val intervalDelayMinutes: Long = 1

    override suspend fun run() = vurderingService.publishUnpublishedVurderinger()
}
