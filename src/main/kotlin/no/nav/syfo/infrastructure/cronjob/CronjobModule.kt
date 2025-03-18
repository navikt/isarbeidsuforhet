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

    val journalforVurderingerCronjob = JournalforVurderingerCronjob(vurderingService)
    cronjobs.add(journalforVurderingerCronjob)

    val publishForhandsvarselCronjob = PublishForhandsvarselCronjob(varselService = varselService)
    cronjobs.add(publishForhandsvarselCronjob)

    val publishVurderingerCronJob = PublishVurderingerCronjob(vurderingService = vurderingService)
    cronjobs.add(publishVurderingerCronJob)

    if (environment.republishForhandsvarselWithAdditionalInfoCronjobEnabled) {
        val republishForhandsvarselWithAdditionalInfoCronjob = RepublishForhandsvarselWithAdditionalInfoCronjob(
            vurderingService = vurderingService,
            uuids = UUIDS,
        )
        cronjobs.add(republishForhandsvarselWithAdditionalInfoCronjob)
    }
    cronjobs.forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}

val UUIDS = listOf("38eae7a7-c82f-47c9-8bf5-d8634d42f4ac", "1e47f57f-5a0b-488e-b2a2-87976e893147")
