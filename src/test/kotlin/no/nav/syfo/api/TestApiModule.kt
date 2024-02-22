package no.nav.syfo.api

import io.ktor.server.application.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.infrastructure.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.wellKnownInternalAzureAD

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val database = externalMockEnvironment.database
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.istilgangskontroll,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = wellKnownInternalAzureAD(),
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
        database = database,
    )
}
