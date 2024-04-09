package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.service.VarselService

class PublishExpiredForhandsvarslerCronjob(
    private val varselService: VarselService,
) : Cronjob {
    override val initialDelayMinutes: Long = 3
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() = varselService.publishExpiredForhandsvarsler()
}
