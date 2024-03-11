package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.application.service.VarselService
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.infrastructure.clients.leaderelection.LeaderPodClient
import no.nav.syfo.launchBackgroundTask

fun launchCronjobs(
    applicationState: ApplicationState,
    environment: Environment,
    vurderingService: VurderingService,
    varselService: VarselService,
) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient,
    )
    val cronjobs = mutableListOf<Cronjob>()

    val journalforForhandsvarselCronjob = JournalforForhandsvarselCronjob(vurderingService)
    cronjobs.add(journalforForhandsvarselCronjob)

    val publiserForhandsvarselCronjob = PubliserForhandsvarselCronjob(varselService = varselService)
    cronjobs.add(publiserForhandsvarselCronjob)

    val publishExpiredForhandsvarslerCronjob = PublishExpiredForhandsvarslerCronjob(varselService = varselService)
    cronjobs.add(publishExpiredForhandsvarslerCronjob)

    cronjobs.forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
