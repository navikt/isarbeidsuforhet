package no.nav.syfo.api

import io.ktor.server.application.*
import io.mockk.mockk
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.application.IVurderingProducer
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.clients.pdfgen.VurderingPdfService
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.journalforing.JournalforingService

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
    val varselPdfService = VurderingPdfService(
        pdfGenClient = externalMockEnvironment.pdfgenClient,
        pdlClient = externalMockEnvironment.pdlClient,
    )
    val journalforingService = JournalforingService(
        dokarkivClient = externalMockEnvironment.dokarkivClient,
        pdlClient = externalMockEnvironment.pdlClient,
    )

    val vurderingService = VurderingService(
        vurderingRepository = vurderingRepository,
        vurderingPdfService = varselPdfService,
        journalforingService = journalforingService,
        vurderingProducer = mockk<IVurderingProducer>(),
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
        database = database,
        vurderingService = vurderingService,
    )
}
