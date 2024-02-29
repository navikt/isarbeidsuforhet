package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.application.service.VarselService
import no.nav.syfo.infrastructure.leaderelection.LeaderPodClient
import no.nav.syfo.launchBackgroundTask

fun launchCronjobs(environment: Environment, applicationState: ApplicationState, varselService: VarselService) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )
    val cronjobs = mutableListOf<Cronjob>()

    if (environment.publishForhandsvarselEnabled) {
        val publiserForhandsvarselCronjob = PubliserForhandsvarselCronjob(varselService = varselService)
        cronjobs.add(publiserForhandsvarselCronjob)
    }

    cronjobs.forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
