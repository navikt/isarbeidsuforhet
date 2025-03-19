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

val UUIDS = listOf(
    "9db72978-e5ea-4b78-bd64-232635ad79bd",
    "a58440a7-f2f6-441b-8fc7-2f6402cdb8c9",
    "4b018f3f-db57-4be6-bd7e-99bb7ba369a0",
    "d819ad96-2f46-4c60-b24d-2f03f5e6e159",
    "b035c5c9-89d7-4b7a-b0fb-2dd0e3cdf885",
    "0a52e4c8-507f-4f4a-8919-b3efc203c284",
    "034d1e2f-68d0-493d-8bba-4816ec1f9823",
    "8d7d8d11-518a-499e-a55d-f6b70f3e428d",
    "55ea3696-e7f4-419b-9dfd-53e4d7564df1",
    "9666d05f-1e32-43ba-bd49-ac5f9e8232e3",
    "e6206b7e-bfe0-4ebf-a3b9-c6b6e7bfdb4d",
    "3bcf34be-fcf2-4ffc-a569-88ba92c6c628",
    "dec0190e-6d6f-4471-bc72-3dfc69362ab8",
    "c4d20bf1-3dc7-4f81-96a4-91dd184a1e34",
    "ad697ddf-2a87-4803-8527-bdc81cdc704a",
    "9aeec2a3-5596-4f21-9fc9-dde55b5bb9a6",
    "0ccae971-22cc-4f55-b735-0b4504fc7316",
    "2bb189d6-d8a2-4c9b-8015-ee2518c263fa",
    "0282fa83-30ff-4f2f-a9a6-cf6c543c3e89",
    "83c21643-6166-49bc-b1e8-dcbbacb86726",
    "8187c795-ebc1-4298-b595-1375c136c09c",
    "8309236e-54a6-4d31-a819-4be147d7be16",
    "cc1dc9dc-859d-40ee-bb6c-52175c611d1f",
    "4f734d05-f7dd-4ef5-aecd-71439c294f88"
)
