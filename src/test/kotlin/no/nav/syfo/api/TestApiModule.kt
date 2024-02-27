package no.nav.syfo.api

import io.ktor.server.application.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.application.service.ForhandsvarselService
import no.nav.syfo.infrastructure.database.VurderingRepository
import no.nav.syfo.infrastructure.pdfgen.VarselPdfService
import no.nav.syfo.infrastructure.veiledertilgang.VeilederTilgangskontrollClient

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val database = externalMockEnvironment.database
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.istilgangskontroll,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val vurderingRepository = VurderingRepository(database)
    val varselPdfService = VarselPdfService(
        pdfGenClient = externalMockEnvironment.pdfgenClient,
        pdlClient = externalMockEnvironment.pdlClient,
    )

    val forhandsvarselService = ForhandsvarselService(
        vurderingRepository = vurderingRepository,
        varselPdfService = varselPdfService,
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
        database = database,
        forhandsvarselService = forhandsvarselService,
    )
}
