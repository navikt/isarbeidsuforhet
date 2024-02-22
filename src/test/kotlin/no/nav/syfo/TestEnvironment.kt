package no.nav.syfo

import no.nav.syfo.infrastructure.ClientEnvironment
import no.nav.syfo.infrastructure.ClientsEnvironment
import no.nav.syfo.infrastructure.OpenClientEnvironment
import no.nav.syfo.infrastructure.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment

fun testEnvironment() = Environment(
    database = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "isaktivitetskrav_dev",
        username = "username",
        password = "password",
    ),
    azure = AzureEnvironment(
        appClientId = "isarbeidsuforhet-client-id",
        appClientSecret = "isarbeidsuforhet-secret",
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = "azureOpenIdTokenEndpoint",
    ),
    clients = ClientsEnvironment(
        istilgangskontroll = ClientEnvironment(
            baseUrl = "isTilgangskontrollUrl",
            clientId = "dev-gcp.teamsykefravr.istilgangskontroll",
        ),
        pdl = ClientEnvironment(
            baseUrl = "pdlUrl",
            clientId = "pdlClientId",
        ),
        isarbeidsuforhetpdfgen = OpenClientEnvironment(
            baseUrl = "isarbeidsuforhetpdfgenUrl",
        ),
    ),
    electorPath = "electorPath",
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
