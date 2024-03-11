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

    val publishForhandsvarselCronjob = PublishForhandsvarselCronjob(varselService = varselService)
    cronjobs.add(publishForhandsvarselCronjob)

    val publishExpiredForhandsvarslerCronjob = PublishExpiredForhandsvarslerCronjob(varselService = varselService)
    cronjobs.add(publishExpiredForhandsvarslerCronjob)

    val publishVurderingerCronJob = PublishVurderingerCronjob(vurderingService = vurderingService)
    // TODO: kommenter inn når dto er validert
    // cronjobs.add(publishVurderingerCronJob)

    cronjobs.forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
