package no.nav.syfo

import no.nav.syfo.infrastructure.azuread.AzureAdClient
import no.nav.syfo.infrastructure.database.TestDatabase
import no.nav.syfo.infrastructure.mock.mockHttpClient
import no.nav.syfo.infrastructure.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.pdl.PdlClient
import no.nav.syfo.infrastructure.wellknown.WellKnown
import java.nio.file.Paths

fun wellKnownInternalAzureAD(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        issuer = "https://sts.issuer.net/veileder/v2",
        jwksUri = uri.toString()
    )
}

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val environment = testEnvironment()
    val mockHttpClient = mockHttpClient(environment = environment)
    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()
    val azureAdClient = AzureAdClient(
        azureEnvironment = environment.azure,
        httpClient = mockHttpClient,
    )
    val pdfgenClient = PdfGenClient(
        pdfGenBaseUrl = environment.clients.isarbeidsuforhetpdfgen.baseUrl,
        httpClient = mockHttpClient,
    )
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlEnvironment = environment.clients.pdl,
        httpClient = mockHttpClient,
    )
    companion object {
        val instance: ExternalMockEnvironment = ExternalMockEnvironment()
    }
}
